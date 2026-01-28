package usr.skyswimmer.quickff.tools.quickff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import usr.skyswimmer.githubwebhooks.api.apps.GithubApp;
import usr.skyswimmer.githubwebhooks.api.apps.GithubAppInstallationTokens;
import usr.skyswimmer.githubwebhooks.api.util.FileUtils;
import usr.skyswimmer.githubwebhooks.api.util.HashUtils;
import usr.skyswimmer.githubwebhooks.api.util.JsonUtils;
import usr.skyswimmer.githubwebhooks.api.util.tasks.async.AsyncTaskManager;
import usr.skyswimmer.quickff.tools.entities.AutoFfConfig;
import usr.skyswimmer.quickff.tools.entities.WebhookPushEventEntity;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
					logger.info("Preparing repository...");
					File repoPath = repoMemory.repoDir;
					File gitCache = new File(repoPath, ".git");
					Git client = null;
					try {
						if (!gitCache.exists()) {
							// Clone
							logger.info("Cloning " + repoMemory.name + "...");
							client = Git.cloneRepository().setURI(push.repository.httpUrl).setDirectory(repoPath)
									.setCredentialsProvider(createCredentialProvider(app, push.installation.id))
									.setNoCheckout(true).call();
							logger.info("Completed successfully!");
						} else {
							// Fetch
							logger.info("Fetching " + repoMemory.name + "...");
							client = Git.open(repoPath);
							client.fetch().setCredentialsProvider(createCredentialProvider(app, push.installation.id))
									.call();
							logger.info("Completed successfully!");
						}

						// Get repository
						logger.info("Loading repository...");
						Repository repo = client.getRepository();

						// Load ref
						logger.info("Finding branch object....");
						ObjectId id = repo.resolve("refs/remotes/origin/" + branch);

						// Load autoff.json
						logger.info("Finding configuration...");
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
							logger.info("No autoff.json configuration, exiting...");
							return;
						}

						// Get object and close
						ObjectId obj = treeWalk.getObjectId(0);
						treeWalk.close();

						// Get config
						logger.info("Reading configuration...");
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

							// Throw
							throw e;
						}
						sIn.close();

						// Find branch
						logger.info("Matching branch sets...");

						branch = branch;
					} finally {
						// Close
						client.close();
					}
				} catch (Exception e) {
					logger.error("An error occurred running QuickFF, cancelled.", e);
				}
			} finally {
				// Close
				repoMemory.close();
			}
		}
	}

	private static CredentialsProvider createCredentialProvider(GithubApp app, String installationId)
			throws IOException {
		try {
			logger.info("Authenticating application with server...");
			String token = GithubAppInstallationTokens.getOrRequestInstallationAuthToken(app, installationId);
			logger.info("Authentication successful. Beginning process...");
			return new UsernamePasswordCredentialsProvider("x-access-token", token);
		} catch (IOException e) {
			throw new IOException("Authenticating through API failed", e);
		}
	}

}
