package usr.skyswimmer.quickff.tools.quickff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import usr.skyswimmer.githubwebhooks.apps.GithubApp;
import usr.skyswimmer.githubwebhooks.apps.GithubAppInstallationTokens;
import usr.skyswimmer.quickff.tools.entities.AutoFfConfig;
import usr.skyswimmer.quickff.tools.entities.WebhookPushEventEntity;
import usr.skyswimmer.quicktoolsutils.io.FileUtils;
import usr.skyswimmer.quicktoolsutils.io.HashUtils;
import usr.skyswimmer.quicktoolsutils.patterns.PatternMatchResult;
import usr.skyswimmer.quicktoolsutils.patterns.WildcardPatternMatcher;
import usr.skyswimmer.quicktoolsutils.tasks.async.AsyncTaskManager;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class QuickFfRunner {

	private static Logger logger;

	private static boolean inited;
	private static File cacheBase;
	private static HashMap<String, RepoMemoryData> repositoryMemory = new HashMap<String, RepoMemoryData>();

	private static class RepoMemoryData {
		public Object lock = new Object();

		public String name;
		public String fullUrl;
		public File repoDir;

		public boolean isOpen;
		public boolean deleted;

		public long lastTouched;

		public RepoMemoryData(String name, String fullUrl, File repoDir) {
			this.name = name;
			this.fullUrl = fullUrl;
			this.repoDir = repoDir;
			repoDir.mkdirs();
		}

		public void open() {
			isOpen = true;
		}

		public void close() {
			lastTouched = System.currentTimeMillis();
			isOpen = false;
		}
	}

	private static void init(File workingDirBase) {
		if (inited)
			return;
		inited = true;

		// Set up logger
		logger = LogManager.getLogger("quickff");

		// Go through cache
		cacheBase = new File(workingDirBase, "repository-temp");
		cacheBase.mkdirs();
		for (File dir : cacheBase.listFiles(t -> t.isDirectory())) {
			// Remove
			logger.info("Clearing cache: " + dir.getName());
			FileUtils.deleteDir(dir);
		}

		// Cache remover
		AsyncTaskManager.runAsync(() -> {
			while (true) {
				// Go through repositories
				RepoMemoryData[] repos;
				synchronized (repositoryMemory) {
					repos = repositoryMemory.values().toArray(t -> new RepoMemoryData[t]);
				}
				for (RepoMemoryData repo : repos) {
					// Check first outside of lock to avoid unneeded blocking
					if (!repo.isOpen && (System.currentTimeMillis() - repo.lastTouched) >= (60 * 60 * 1000)) {
						// Clear after an hour
						synchronized (repo.lock) {
							// Re-check just in case
							if (!repo.isOpen && (System.currentTimeMillis() - repo.lastTouched) >= (60 * 60 * 1000)) {
								// Delete folder
								logger.info("Cleaning repository " + repo.name + ": not touched in the last hour...");
								if (repo.repoDir.exists())
									FileUtils.deleteDir(repo.repoDir);
								repo.deleted = true;

								// Remove from memory
								synchronized (repositoryMemory) {
									repositoryMemory.remove(repo.fullUrl);
								}
							}
						}
					}
				}

				// Wait
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
		});
	}

	public static void downloadAndRun(File workingDirBase, String branch, WebhookPushEventEntity push, GithubApp app) {
		// Init
		init(workingDirBase);

		// Set up locks
		RepoMemoryData repoMemory;
		try {
			synchronized (repositoryMemory) {
				if (!repositoryMemory.containsKey(push.repository.httpUrl))
					repositoryMemory.put(push.repository.httpUrl, new RepoMemoryData(push.repository.fullName,
							push.repository.httpUrl,
							new File(cacheBase, HashUtils.sha256Hash(push.repository.httpUrl.getBytes("UTF-8")))));
				repoMemory = repositoryMemory.get(push.repository.httpUrl);
				repoMemory.open();
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Synchronize to repository
		synchronized (repoMemory.lock) {
			try {
				// Check deleted
				if (repoMemory.deleted) {
					// We just missed the lock from the repo cleanup, call again
					downloadAndRun(workingDirBase, branch, push, app);
					return;
				}

				// Log start
				logger.info("Starting QuickFF runner for repository " + push.repository.fullName + " for branch "
						+ branch + "...");

				// Get credentials
				try {
					// Prepare repository
					logger.info("[" + repoMemory.name + "] Preparing repository...");
					File repoPath = repoMemory.repoDir;
					File gitCache = new File(repoPath, ".git");
					Git client = null;
					try {
						if (!gitCache.exists()) {
							// Clone
							logger.info("[" + repoMemory.name + "] Cloning " + repoMemory.name + "...");
							client = Git.cloneRepository().setURI(push.repository.httpUrl).setDirectory(repoPath)
									.setCredentialsProvider(createCredentialProvider(repoMemory, app,
											push.installation.id, "Cloning " + repoMemory.name + "..."))
									.setNoCheckout(true).call();
							logger.info("[" + repoMemory.name + "] Completed successfully!");
						} else {
							// Fetch
							logger.info("[" + repoMemory.name + "] Fetching " + repoMemory.name + "...");
							client = Git.open(repoPath);
							client.fetch().setCredentialsProvider(createCredentialProvider(repoMemory, app,
									push.installation.id, "Fetching " + repoMemory.name + "...")).call();
							logger.info("[" + repoMemory.name + "] Completed successfully!");
						}

						// Get repository
						logger.info("[" + repoMemory.name + "] Loading repository...");
						Repository repo = client.getRepository();

						// Load ref
						logger.info("[" + repoMemory.name + "] Finding branch object....");
						ObjectId id = repo.resolve("refs/remotes/origin/" + branch);
						if (id == null) {
							// Close
							logger.info("[" + repoMemory.name + "] Branch not found, exiting...");
							return;
						}

						// Load autoff.json
						logger.info("[" + repoMemory.name + "] Finding configuration...");
						RevWalk revWalk = new RevWalk(repo);
						RevCommit currentCommit = revWalk.parseCommit(id);
						revWalk.close();
						RevTree tree = currentCommit.getTree();
						TreeWalk treeWalk = new TreeWalk(repo);
						treeWalk.addTree(tree);
						treeWalk.setRecursive(true);
						treeWalk.setFilter(PathFilter.create("autoff.json"));
						if (!treeWalk.next()) {
							// Not found
							treeWalk.close();

							// Close
							logger.info("[" + repoMemory.name + "] No autoff.json configuration, exiting...");
							return;
						}

						// Get object and close
						ObjectId obj = treeWalk.getObjectId(0);
						treeWalk.close();

						// Get config
						logger.info("[" + repoMemory.name + "] Reading configuration...");
						ObjectLoader objR = repo.open(obj);
						InputStream sIn = objR.openStream();
						InputStreamReader reader = new InputStreamReader(sIn);
						AutoFfConfig config = new AutoFfConfig();
						try {
							JsonObject confJson = JsonParser.parseReader(reader).getAsJsonObject();
							config.loadFromJson(confJson, "autoff.json");
						} catch (Exception e) {
							// Error
							sIn.close();

							// Send comment to commit
							try {
								JsonObject payload = new JsonObject();
								payload.addProperty("body",
										"An error occurred while parsing the QuickFF configuration autoff.json file, please verify the configuration.\n\n```\nError: "
												+ e.getMessage() + "\n```");
								app.appInstallationApiRequest(push.installation.id, "/repos/" + push.repository.fullName
										+ "/commits/" + currentCommit.getName() + "/comments", "POST", payload);
							} catch (IOException e2) {
							}

							// Send failed check
							try {
								JsonObject payload = new JsonObject();
								payload.addProperty("state", "error");
								payload.addProperty("context", "QuickFF");
								payload.addProperty("description", "Configuration error in autoff.json");
								app.appInstallationApiRequest(push.installation.id,
										"/repos/" + push.repository.fullName + "/statuses/" + currentCommit.getName(),
										"POST", payload);
							} catch (IOException e2) {
							}

							// Throw
							throw e;
						}
						sIn.close();
						if (!config.enabled) {
							logger.info("[" + repoMemory.name + "] QuickFF was disabled, exiting...");
							return;
						}

						// Find branch
						logger.info("[" + repoMemory.name + "] Finding matching branch sets...");
						String selectedPattern = null;
						String[] outputBranches = null;
						String[] parameters = new String[0];
						for (String pattern : config.branches.keySet()) {
							String ent = pattern;
							if (pattern.startsWith("RXM:")) {
								// Regex matcher
								if (pattern.startsWith("RXM:"))
									pattern = pattern.substring("RXM:".length());

								// Match
								if (branch.matches(pattern)) {
									// Found
									selectedPattern = pattern;
									outputBranches = config.branches.get(ent);
									logger.info("[" + repoMemory.name + "] Matched branch set: " + ent);
									break;
								}
							} else if (pattern.startsWith("WCM:")
									|| (!pattern.startsWith("RAW:") && pattern.contains("*"))) {
								// Wildcard matcher
								if (pattern.startsWith("WCM:"))
									pattern = pattern.substring("WCM:".length());

								// Match pattern
								WildcardPatternMatcher matcher = new WildcardPatternMatcher(pattern);
								PatternMatchResult res = matcher.match(branch);
								if (res.isMatch()) {
									// Found
									selectedPattern = pattern;
									outputBranches = config.branches.get(ent);
									parameters = res.getParameters();
									logger.info("[" + repoMemory.name + "] Matched branch set: " + ent);
									break;
								}
							} else {
								// Raw
								if (pattern.startsWith("RAW:"))
									pattern = pattern.substring("RAW:".length());

								// Check
								if (pattern.equalsIgnoreCase(branch)) {
									// Found
									selectedPattern = pattern;
									outputBranches = config.branches.get(ent);
									logger.info("[" + repoMemory.name + "] Matched branch set: " + ent);
									break;
								}
							}
						}
						if (outputBranches != null && outputBranches.length != 0) {
							// Go through target branches
							String[] targets = new String[outputBranches.length];
							for (int i = 0; i < targets.length; i++) {
								String target = outputBranches[i];
								int ind = 0;
								for (String param : parameters) {
									target = target.replace("{" + (ind++ + 1) + "}", param);
								}
								targets[i] = target;
							}

							// Found matches
							String branchesToPushTo = "";
							for (String target : targets) {
								if (!branchesToPushTo.isEmpty())
									branchesToPushTo += ", ";
								branchesToPushTo += target;
							}
							logger.info("[" + repoMemory.name + "] Found list of branches to fast-forward: "
									+ branchesToPushTo);

							// Push for branches
							String failedBranches = "";
							int i = 0;
							for (String target : targets) {
								// Log
								logger.info("[" + repoMemory.name + "] Checking if needing to fast-forward " + target
										+ "...");
								String outputBranch = outputBranches[i++];

								// Get branch
								ObjectId targetId = repo.resolve("refs/remotes/origin/" + target);
								if (targetId == null) {
									// Close
									logger.info("[" + repoMemory.name + "] Branch not found, skipping...");
									continue;
								}

								// Get last commit
								revWalk = new RevWalk(repo);
								RevCommit lastCommit = revWalk.parseCommit(targetId);
								revWalk.close();
								logger.info("[" + repoMemory.name + "] Last commit of " + target + ": "
										+ lastCommit.getName());

								// Check if target is up to date by checking if the current branch's commit is
								// present in the target branch, if so, skip
								boolean found = false;
								List<Ref> refs = client.branchList().setListMode(ListMode.REMOTE)
										.setContains(currentCommit.getName()).call();
								for (Ref ref : refs) {
									String name = ref.getName();
									if (name.equals("refs/remotes/origin/" + target)) {
										found = true;
										break;
									}
								}
								if (!found) {
									// Not up to date

									// Check present in current branch
									// We check if the last commit of the target branch is present in the source
									// branch, if so, the source branch is ahead, and the target branch needs to be
									// fastforwarded, if target's last commit is absent from the source branch, the
									// branches diverged and needs a merge
									//
									// By default, autoff only fast-forwards if the target has not diverged and is
									// directly behind the source branch
									//
									// But configuration can allow autoff to merge the source branch into the target
									// branch if the target diverged
									found = false;
									refs = client.branchList().setListMode(ListMode.REMOTE)
											.setContains(lastCommit.getName()).call();
									for (Ref ref : refs) {
										String name = ref.getName();
										if (name.equals("refs/remotes/origin/" + branch)) {
											found = true;
											break;
										}
									}

									// If not found, check hard merge
									String strat = "ff";
									boolean hardMerge = false;
									if (!found && config.hardMergeFor.containsKey(selectedPattern)
											&& Stream.of(config.hardMergeFor.get(selectedPattern))
													.anyMatch(t -> t.equals(outputBranch))) {
										// The commit history doesnt align, but hard merge is preferred
										strat = "merge"; // Use merge by default unless overridden

										// Check strategy
										if (config.hardMergeStrategies.containsKey(selectedPattern)) {
											// Get
											Object val = config.hardMergeStrategies.get(selectedPattern);
											if (val instanceof HashMap) {
												// Map
												// Get by branch
												HashMap<String, String> map = (HashMap<String, String>) val;
												if (map.containsKey(outputBranch)) {
													// Use for pattern
													strat = map.get(outputBranch);
												} else if (map.containsKey("*")) {
													// Use default
													strat = map.get("*");
												}
											} else {
												// String
												// Default
												strat = val.toString();
											}
										}

										// Needs fast forward
										found = true;

										// Check
										if (strat.equalsIgnoreCase("ff") || strat.equalsIgnoreCase("forceff")) {
											// Fast-forward if possible
											if (strat.equalsIgnoreCase("ff")) {
												// Regular fastforward is not possible as branches do not align
												found = false;
											}

											// Forceff checks file contents and then rebases if there are no differences
											hardMerge = false;
										} else {
											// Hard merge
											hardMerge = true;
										}
									}

									// Check result
									if (found) {
										// Log
										try {
											if (!hardMerge)
												logger.info("[" + repoMemory.name + "] Fast-forward needed for "
														+ target + "!");
											else
												logger.info(
														"[" + repoMemory.name + "] Merge needed for " + target + "!");

											// Fast-forward
											boolean merged = true;
											try {
												// Checkout
												logger.info("[" + repoMemory.name + "] Checking out " + target + "...");
												ObjectId currentBranchHead = repo.resolve("refs/heads/" + branch);
												if (currentBranchHead == null) {
													// Checkout new
													client.reset().setMode(ResetType.HARD)
															.setRef("origin/" + repo.getBranch()).call();
													client.checkout().setName(branch).setCreateBranch(true)
															.setUpstreamMode(SetupUpstreamMode.TRACK)
															.setStartPoint("origin/" + branch).call();
												} else {
													// Checkout existing
													client.checkout().setName(branch).call();

													// Update
													logger.info("[" + repoMemory.name + "] Updating " + target + "...");
													if (!client.pull().setRemote("origin").setRemoteBranchName(branch)
															.setCredentialsProvider(createCredentialProvider(repoMemory,
																	app, push.installation.id,
																	"Pulling " + branch + " from upstream..."))
															.call().isSuccessful())
														throw new IOException(
																"Pull from branch " + branch + " did not succeed");
												}
												client.reset().setMode(ResetType.HARD).setRef("origin/" + branch)
														.call();
												ObjectId currentBranch = repo.resolve("refs/heads/" + target);
												if (currentBranch == null) {
													// Checkout new
													client.checkout().setName(target).setCreateBranch(true)
															.setUpstreamMode(SetupUpstreamMode.TRACK)
															.setStartPoint("origin/" + target).call();
												} else {
													// Checkout existing
													client.checkout().setName(target).call();
													client.reset().setMode(ResetType.HARD).setRef("origin/" + target)
															.call();

													// Update
													logger.info("[" + repoMemory.name + "] Updating " + target + "...");
													if (!client.pull().setRemote("origin").setRemoteBranchName(target)
															.setCredentialsProvider(createCredentialProvider(repoMemory,
																	app, push.installation.id,
																	"Pulling " + target + " from upstream..."))
															.call().isSuccessful())
														throw new IOException(
																"Pull from branch " + target + " did not succeed");
												}

												// Pull
												if (!hardMerge)
													logger.info("[" + repoMemory.name + "] Fast-forwarding " + target
															+ " from " + branch + "...");
												else
													logger.info("[" + repoMemory.name + "] Merging " + branch + " into "
															+ target + "...");
												if (!hardMerge && strat.equalsIgnoreCase("ff")) {
													// Use fastforwarding through pull
													if (!client.pull().setRemote("origin").setRemoteBranchName(branch)
															.setCredentialsProvider(createCredentialProvider(repoMemory,
																	app, push.installation.id,
																	"Fast-forwarding " + target + "..."))
															.setFastForward(FastForwardMode.FF_ONLY).call()
															.isSuccessful())
														throw new IOException(
																"Pull from branch " + target + " did not succeed");

												} else {
													// Check if using forceff
													if (strat.equalsIgnoreCase("forceff")) {
														// Forced fastforward
														//
														// Basically, if the file contents match, ditch commit history,
														// and fast forward
														//
														// This strategy only works to sync up merge squash commits down
														// to the source branch they came from, it will not allow
														// rebasing, or fastforwarding, if the contents do not match
														//
														// It will use regular fastforward when the current HEAD is
														// present in the target branch's head, that is because the
														// merge strategy is always ff unless the heads are out of sync,
														// this means forcedff isnt used if the branch can be
														// fastforwarded directly!!!
														//
														// So no need to check if the branch can be fastforwarded
														// normally, we only need to check if contents match between
														// branches and if so, ditch commit history and sync up
														//
														// Futhermore we can depend on the commit history being in sync
														// with the target branch, as a pull is already done!

														// Find last commit prior to divergence if any
														// Basically, walk source branch, find the first commit thats
														// present in target
														//
														// This is to check if the branches used to be in sync
														revWalk = new RevWalk(repo);
														RevCommit latestSource = revWalk
																.parseCommit(repo.resolve("refs/heads/" + branch));
														revWalk.markStart(latestSource);
														RevCommit c = revWalk.next();
														RevCommit lastCommon = null;
														while (c != null) {
															// Check if the commit is present
															boolean f = false;
															refs = client.branchList().setListMode(ListMode.REMOTE)
																	.setContains(c.getName()).call();
															for (Ref ref : refs) {
																String name = ref.getName();
																if (name.equals("refs/remotes/origin/" + target)) {
																	f = true;
																	break;
																}
															}
															if (f) {
																// The branches match at this point
																lastCommon = c;
																break;
															}

															// Next
															c = revWalk.next();
														}

														// Check if found a common commit
														merged = false;
														if (lastCommon != null) {
															// Found a common commit

															// Check if the contents between the two branches are the
															// same even if commit history is not
															logger.info("[" + repoMemory.name
																	+ "] Comparing content of branch " + target
																	+ " with " + branch + "...");
															List<DiffEntry> diff = client.diff()
																	.setOldTree(getTreeIterator(repo,
																			repo.exactRef("refs/heads/" + branch)))
																	.setNewTree(getTreeIterator(repo,
																			repo.exactRef("refs/heads/" + target)))
																	.setShowNameAndStatusOnly(true)
																	.call();
															if (diff.size() == 0) {
																// No difference

																// Fast forward
																logger.info(
																		"[" + repoMemory.name + "] Content of " + branch
																				+ " matches " + target
																				+ "! Fast-forwarding history...");
																client.reset().setMode(ResetType.HARD)
																		.setRef("origin/" + branch).call();
																merged = true;
																logger.info("[" + repoMemory.name
																		+ "] Merge succeeded, forcing push...");
																for (PushResult res : client.push().setForce(true)
																		.setCredentialsProvider(
																				createCredentialProvider(repoMemory,
																						app, push.installation.id,
																						"Pushing " + target
																								+ " to upstream..."))
																		.call()) {
																	for (RemoteRefUpdate update : res
																			.getRemoteUpdates()) {
																		if (update
																				.getStatus() != RemoteRefUpdate.Status.OK
																				&& update
																						.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
																			String messages = update.getMessage();
																			throw new IOException(
																					"Push command failed, remote did not accept the request",
																					messages != null
																							? new IOException(messages)
																							: null);
																		}
																	}
																}
															} else {
																// Diverged
																logger.info("[" + repoMemory.name
																		+ "] Fast-forward not possible for " + target
																		+ "! Branches diverged!");
															}
														} else {
															// Diverged
															logger.info("[" + repoMemory.name
																	+ "] Fast-forward not possible for " + target
																	+ "! Branches diverged!");
														}
													} else {
														// Merge with strategy

														// Get name
														String name = app.appApiRequest("/app", "GET", null).get("slug")
																.getAsString();
														String uId = app.apiRequest(
																"/users/" + URLEncoder.encode(name + "[bot]", "UTF-8"),
																"GET", null).get("id").getAsString();

														// Do merge
														if (strat.equalsIgnoreCase("merge")) {
															// Merge
															client.merge().include(repo.resolve(branch))
																	.setCommit(false)
																	.setFastForward(FastForwardMode.FF).call();

															// Commit merge
															client.commit()
																	.setAuthor(name + "[bot]",
																			uId + "+" + name
																					+ "[bot]@users.noreply.github.com")
																	.setCommitter(name + "[bot]",
																			uId + "+" + name
																					+ "[bot]@users.noreply.github.com")
																	.setMessage("Merging " + branch + " into " + target)
																	.call();
														} else if (strat.equalsIgnoreCase("rebase")) {
															// Rebase
															if (!client.pull().setRebase(BranchRebaseMode.REBASE)
																	.setRemote("origin")
																	.setRemoteBranchName(branch)
																	.setCredentialsProvider(
																			createCredentialProvider(repoMemory,
																					app, push.installation.id,
																					"Rebasing " + branch + " into "
																							+ target
																							+ "..."))
																	.setFastForward(FastForwardMode.FF_ONLY).call()
																	.isSuccessful()) {
																throw new IOException(
																		"Pull and rebase from branch " + target
																				+ " did not succeed");
															}
														}
													}
												}

												// Merge succeeded
												if (merged) {
													logger.info("[" + repoMemory.name
															+ "] Merge succeeded, preparing to push...");
													for (PushResult res : client.push()
															.setCredentialsProvider(createCredentialProvider(repoMemory,
																	app, push.installation.id,
																	"Pushing " + target + " to upstream..."))
															.call()) {
														for (RemoteRefUpdate update : res.getRemoteUpdates()) {
															if (update.getStatus() != RemoteRefUpdate.Status.OK
																	&& update
																			.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
																String messages = update.getMessage();
																throw new IOException(
																		"Push command failed, remote did not accept the request",
																		messages != null ? new IOException(messages)
																				: null);
															}
														}
													}
												}
											} catch (Exception e) {
												// Log
												logger.error("[" + repoMemory.name
														+ "] An error occurred while fast-forwarding, cancelled.", e);

												// Save error
												if (!failedBranches.isEmpty())
													failedBranches += "\n";
												failedBranches += " - " + target + ": " + e.getMessage();
											} finally {
												if (merged) {
													ObjectId currentBranchHead = repo.resolve("refs/heads/" + branch);
													if (currentBranchHead == null) {
														// Checkout new
														client.reset().setMode(ResetType.HARD)
																.setRef("origin/" + repo.getBranch()).call();
														client.checkout().setName(branch).setCreateBranch(true)
																.setUpstreamMode(SetupUpstreamMode.TRACK)
																.setStartPoint("origin/" + branch).call();
													} else {
														// Checkout existing
														client.checkout().setName(branch).call();

														// Update
														logger.info(
																"[" + repoMemory.name + "] Updating " + branch + "...");
														if (!client.pull().setRemote("origin")
																.setRemoteBranchName(branch)
																.setCredentialsProvider(
																		createCredentialProvider(repoMemory,
																				app, push.installation.id,
																				"Pulling " + branch
																						+ " from upstream..."))
																.call().isSuccessful())
															throw new IOException(
																	"Pull from branch " + branch + " did not succeed");
													}
													client.reset().setMode(ResetType.HARD).setRef("origin/" + branch)
															.call();
												}
											}
										} catch (Exception e) {
											// Log
											logger.error(
													"[" + repoMemory.name
															+ "] An error occurred while fast-forwarding, cancelled.",
													e);

											// Save error
											if (!failedBranches.isEmpty())
												failedBranches += "\n";
											failedBranches += " - " + target + ": " + e.getMessage();
										}
									} else {
										logger.info("[" + repoMemory.name + "] Fast-forward not possible for " + target
												+ "! Branches diverged!");
									}
								} else {
									logger.info(
											"[" + repoMemory.name + "] Branch " + target + " is already up to date");
								}
							}

							// Check result
							logger.info("[" + repoMemory.name + "] Finished!");
							if (!failedBranches.isEmpty()) {
								// Log
								logger.error(
										"Some branches could not be fast-forwarded due to errors that occurred during the merge process:\n"
												+ failedBranches);

								// Send comment to commit
								try {
									JsonObject payload = new JsonObject();
									payload.addProperty("body",
											"Some branches could not be fast-forwarded due to errors that occurred during the merge process:\n"
													+ failedBranches);
									app.appInstallationApiRequest(push.installation.id,
											"/repos/" + push.repository.fullName + "/commits/" + currentCommit.getName()
													+ "/comments",
											"POST", payload);
								} catch (IOException e2) {
								}

								// Send failed check
								try {
									JsonObject payload = new JsonObject();
									payload.addProperty("state", "error");
									payload.addProperty("context", "QuickFF");
									payload.addProperty("description", "Fast-forwarding failed");
									app.appInstallationApiRequest(push.installation.id, "/repos/"
											+ push.repository.fullName + "/statuses/" + currentCommit.getName(), "POST",
											payload);
								} catch (IOException e2) {
								}
							}
						} else {
							// No targets found
							logger.info("[" + repoMemory.name + "] Branch did not match any configured set, ignored.");
						}
					} finally {
						// Close
						if (client != null)
							client.close();
					}
				} catch (Exception e) {
					logger.error("[" + repoMemory.name + "] An error occurred running QuickFF, cancelled.", e);
				}
			} finally {
				// Close
				repoMemory.close();
			}
		}
	}

	private static AbstractTreeIterator getTreeIterator(Repository repo, Ref ref) throws IOException {
		RevWalk walk = new RevWalk(repo);
		try {
			// Load
			RevCommit lastCommit = walk.parseCommit(ref.getObjectId());
			RevTree tree = walk.parseTree(lastCommit.getTree().getId());
			CanonicalTreeParser parser = new CanonicalTreeParser();
			ObjectReader reader = repo.newObjectReader();
			parser.reset(reader, tree.getId());
			reader.close();
			return parser;
		} finally {
			walk.close();
		}
	}

	private static CredentialsProvider createCredentialProvider(RepoMemoryData repoMemory, GithubApp app,
			String installationId, String event) throws IOException {
		try {
			logger.info("[" + repoMemory.name + "] Authenticating application with server...");
			String token = GithubAppInstallationTokens.requestInstallationAuthToken(app, installationId);
			logger.info("[" + repoMemory.name + "] Authentication successful!");
			logger.info("[" + repoMemory.name + "] " + event);
			return new UsernamePasswordCredentialsProvider("x-access-token", token);
		} catch (IOException e) {
			throw new IOException("Authenticating through API failed", e);
		}
	}

}
