package usr.skyswimmer.quickff.tools.quickff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import usr.skyswimmer.githubwebhooks.api.apps.GithubApp;
import usr.skyswimmer.githubwebhooks.api.apps.GithubAppInstallationTokens;
import usr.skyswimmer.githubwebhooks.api.util.FileUtils;
import usr.skyswimmer.githubwebhooks.api.util.HashUtils;
import usr.skyswimmer.githubwebhooks.api.util.patterns.PatternMatchResult;
import usr.skyswimmer.githubwebhooks.api.util.patterns.WildcardPatternMatcher;
import usr.skyswimmer.githubwebhooks.api.util.tasks.async.AsyncTaskManager;
import usr.skyswimmer.quickff.tools.entities.AutoFfConfig;
import usr.skyswimmer.quickff.tools.entities.WebhookPushEventEntity;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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
		public File repoDir;

		public boolean isOpen;
		public boolean deleted;

		public long lastTouched;

		public RepoMemoryData(String name, File repoDir) {
			this.name = name;
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
									repositoryMemory.remove(repo.name);
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
				if (!repositoryMemory.containsKey(push.repository.fullName))
					repositoryMemory.put(push.repository.fullName, new RepoMemoryData(push.repository.fullName,
							new File(cacheBase, HashUtils.sha256Hash(push.repository.fullName.getBytes("UTF-8")))));
				repoMemory = repositoryMemory.get(push.repository.fullName);
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
							// FIXME

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
							for (String target : targets) {
								// Log
								logger.info("[" + repoMemory.name + "] Checking if needing to fast-forward " + target
										+ "...");

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

								// Check present in current branch
								boolean found = false;
								List<Ref> refs = client.branchList().setListMode(ListMode.REMOTE)
										.setContains(lastCommit.getName()).call();
								for (Ref ref : refs) {
									String name = ref.getName();
									if (name.equals("refs/remotes/origin/" + branch)) {
										found = true;
										break;
									}
								}

								// Check result
								if (found) {
									// Log
									try {
										logger.info(
												"[" + repoMemory.name + "] Fast-forward needed for " + target + "!");

										// Fast-forward
										try {
											// Checkout
											logger.info("[" + repoMemory.name + "] Checking out " + target + "...");
											client.checkout().setName(branch).call();
											client.reset().setMode(ResetType.HARD).setRef("origin/" + branch).call();
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
												client.pull().setRemote("origin").setRemoteBranchName(target)
														.setCredentialsProvider(createCredentialProvider(repoMemory,
																app, push.installation.id,
																"Pulling " + target + " from upstream..."))
														.call();
											}

											// Pull
											logger.info("[" + repoMemory.name + "] Fast-forwarding " + target + " from "
													+ branch + "...");
											client.pull().setRemote("origin").setRemoteBranchName(branch)
													.setCredentialsProvider(createCredentialProvider(repoMemory, app,
															push.installation.id, "Fast-forwarding " + target + "..."))
													.setFastForward(FastForwardMode.FF_ONLY).call();

											// Merge succeeded
											logger.info(
													"[" + repoMemory.name + "] Merge succeeded, preparing to push...");
											client.push().setCredentialsProvider(createCredentialProvider(repoMemory,
													app, push.installation.id, "Pushing " + target + " to upstream..."))
													.call();
											
										} finally {
											client.checkout().setName(branch).call();
											client.reset().setMode(ResetType.HARD).setRef("origin/" + branch).call();
										}
									} catch (Exception e) {
										// Log
										logger.error("[" + repoMemory.name
												+ "] An error occurred while fast-forwarding, cancelled.", e);

										// Save error
										if (!failedBranches.isEmpty())
											failedBranches += "\n";
										failedBranches += " - " + target + ": " + e.getMessage();
									}
								} else {
									logger.info("[" + repoMemory.name + "] Fast-forward not possible for " + target
											+ "! Branches diverged!");
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
								// FIXME

							}
						} else {
							// No targets found
							logger.info("[" + repoMemory.name + "] Branch did not match any configured set, ignored.");
						}
					} finally {
						// Close
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

	private static CredentialsProvider createCredentialProvider(RepoMemoryData repoMemory, GithubApp app,
			String installationId, String event) throws IOException {
		try {
			logger.info("[" + repoMemory.name + "] Authenticating application with server...");
			String token = GithubAppInstallationTokens.getOrRequestInstallationAuthToken(app, installationId);
			logger.info("[" + repoMemory.name + "] Authentication successful!");
			logger.info("[" + repoMemory.name + "] " + event);
			return new UsernamePasswordCredentialsProvider("x-access-token", token);
		} catch (IOException e) {
			throw new IOException("Authenticating through API failed", e);
		}
	}

}
