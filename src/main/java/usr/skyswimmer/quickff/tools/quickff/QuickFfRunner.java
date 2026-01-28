package usr.skyswimmer.quickff.tools.quickff;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import usr.skyswimmer.githubwebhooks.api.apps.GithubApp;
import usr.skyswimmer.githubwebhooks.api.apps.GithubAppInstallationTokens;
import usr.skyswimmer.githubwebhooks.api.util.FileUtils;
import usr.skyswimmer.githubwebhooks.api.util.HashUtils;
import usr.skyswimmer.githubwebhooks.api.util.tasks.async.AsyncTaskManager;

import usr.skyswimmer.quickff.tools.entities.WebhookPushEventEntity;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

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
					Git client;
					if (!gitCache.exists()) {
						// Clone
						logger.info("Cloning " + repoMemory.name + "...");
						client = Git.cloneRepository().setURI(push.repository.httpUrl).setDirectory(repoPath)
								.setCredentialsProvider(createCredentialProvider(app, push.installation.id))
								.setNoCheckout(true).call();
					} else {
						// Fetch
						logger.info("Fetching " + repoMemory.name + "...");
						client = Git.open(repoPath);
						client.fetch().setCredentialsProvider(createCredentialProvider(app, push.installation.id))
								.call();
						// FIXME
					}
					branch = branch;
				} catch (IOException | GitAPIException e) {
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
			logger.info("Authentication successful.");
			return new UsernamePasswordCredentialsProvider(" x-access-token", token);
		} catch (IOException e) {
			throw new IOException("Authenticating through API failed", e);
		}
	}

}
