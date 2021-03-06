package ext.deployit.community.plugin.lock;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

/**
 * Helper class that acquires locks by creating lock files in a directory.
 */
public class LockHelper implements Serializable {

	private final String LOCK_FILE_DIRECTORY = "locks";

	public boolean atomicallyLock(Collection<ConfigurationItem> cis) {
		Set<ConfigurationItem> acquiredLocks = new HashSet<ConfigurationItem>();
		
		for (ConfigurationItem ci : cis) {
			try {
				if (lock(ci)) {
					acquiredLocks.add(ci);
				}
			} catch(Exception e) {
				// failed to acquire one lock, will clean up later
			}
		}
		
		if (acquiredLocks.size() != cis.size()) {
			// failed to get one or more locks, unlock all acquired locks
			for (ConfigurationItem lockedCi : acquiredLocks) {
				unlock(lockedCi);
			}
		}

		return acquiredLocks.size() == cis.size();
	}

	public void unlock(Set<ConfigurationItem> cisToBeUnlocked) {
		for (ConfigurationItem ci : cisToBeUnlocked) {
			unlock(ci);
		}
	}
	
	public boolean lock(ConfigurationItem ci) throws IOException {
		createLockDirectoryIfNotExists();

		File lockFile = getLockFile(ci);
		if (lockFile.createNewFile()) {
			PrintWriter pw = new PrintWriter(lockFile);
			pw.println("Locking " + ci.getName() + " on " + new Date());
			pw.close();
			return true;
		} else {
			return false;
		}

	}
	
	public void unlock(ConfigurationItem ci) {
		createLockDirectoryIfNotExists();
		
		if (!getLockFile(ci).delete()) {
			throw new RuntimeException("Failed to unlock " + ci.getName());
		}
	}
	
	public boolean isLocked(ConfigurationItem ci) {
		return getLockFile(ci).exists();
	}
	
	public void clearLocks() {
		createLockDirectoryIfNotExists();
		
		for (String lockFile : getLockFileList()) {
			if (! new File(LOCK_FILE_DIRECTORY, lockFile).delete()) {
				throw new RuntimeException("Unable to delete lock file " + lockFile);
			}
		}
	}

	public List<String> listLocks() {
		createLockDirectoryIfNotExists();
		
		return newArrayList(transform(getLockFileList(), new Function<String, String>() {
			@Override
			public String apply(String input) {
				return lockFileNameToCiId(input);
			}
		}));
	}

	private List<String> getLockFileList() {
		return newArrayList(new File(LOCK_FILE_DIRECTORY).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".lock");
			}
		}));
	}

	private File getLockFile(ConfigurationItem ci) {
		return new File(LOCK_FILE_DIRECTORY, ciIdToLockFileName(ci.getId()));
	}

	String ciIdToLockFileName(String ciId) {
		return ciId.replaceAll("/", "\\$") + ".lock";
	}

	String lockFileNameToCiId(String lockFileName) {
		return lockFileName.replaceAll("\\$", "/").replace(".lock", "");
	}
	
	private File createLockDirectoryIfNotExists() {
		return createLockDirectoryIfNotExists(LOCK_FILE_DIRECTORY);
	}
	
	private File createLockDirectoryIfNotExists(String directory) {
		File lockDir = new File(directory);
		if (lockDir.exists() && lockDir.isDirectory()) {
			return lockDir;
		}
		
		lockDir.mkdir();
		
		if (!(lockDir.exists() && lockDir.isDirectory())) {
			throw new RuntimeException("Unable to create lock directory");
		}
		
		return lockDir;
		
	}
}
