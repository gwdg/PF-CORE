/* $Id: SyncProfile.java,v 1.5 2005/11/04 13:59:58 schaatser Exp $
 */
package de.dal33t.powerfolder.disk;

import java.util.*;
import java.io.Serializable;

import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Loggable;
import de.dal33t.powerfolder.message.Invitation;

/**
 * Instance of this class describe how a folder should be synchronized with the
 * remote sides.
 *
 * Profiles are shared within PowerFolder. There should never be two profiles
 * with identical configurations or name. Thus if a folder has a particular
 * profile, and that profile is edited, all other folders that have the same
 * profile are directly affected. It is illegal to set a profile's profileName
 * the same as another profile.
 *
 * SyncProfile maintains two static caches, one for preset (non-editable)
 * profiles, and one for custom profiles. The preset profiles and the preset
 * cache can not be modified. Both caches are protected inside this class and
 * are not intended for direct external modification. Access to the custom
 * caches should always be synchronized.
 *
 * SyncProfile has no public constructor. Custom profiles can be created using
 * the retrieveSyncProfile() method. These will first try to find from the
 * caches a profile with the same internal configuration as requested. Failing
 * this a new (custom) profile will be created and is added to the custom cache.
 * Note that if a matching profile is found in one of the caches, the
 * profileName of the returned profile will probably not equal the
 * profileNameArg supplied.
 *
 * Profiles can be saved and loaded as comma-separated lists. Note that the old
 * way to store a profile was as a simple 'profile id'. getSyncProfileByFieldList()
 * still supports this method for backward compatability. If a profile is loaded
 * from the config file and has the same name but different internal
 * configuration as another profile already in one of the caches, then it is
 * given an auto-generated name by adding a unique number to the profileName,
 * like 'Custom profile 3'.
 *
 * Preset profiles always get their name from an id. This ensures that the
 * true translated name is showen if the language is changed (restart).
 *
 * Do not serialize SyncProfiles. They will not be accepted into the caches
 * on the target system when deserialized. Use getFieldList() to transfer.
 * This implements Serializable ONLY for compliance with old Invitations.
 *
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.5 $
 * @see Invitation
 */
public class SyncProfile extends Loggable implements Serializable {

    private static final long serialVersionUID = 100L;

    /** Field delimiter for field list */
    public static final String FIELD_LIST_DELIMITER = ",";

    /**
     * Manual download preset profile.
     */
    public static final SyncProfile MANUAL_DOWNLOAD = new SyncProfile(
            "manualdownload", false,
            new SyncProfileConfiguration(false, false, false, false, 30));

    /**
     * Autodownload download preset profile.
     */
    public static final SyncProfile AUTO_DOWNLOAD_FROM_ALL = new SyncProfile(
            "autodownload_all", false,
            new SyncProfileConfiguration(true, true, false, false, 30));

    /**
     * The "Mirror" preset profile.
     * Name still this one because of historic reasons.
     */
    public static final SyncProfile SYNCHRONIZE_PCS = new SyncProfile(
            "syncpcs", false,
            new SyncProfileConfiguration(true, true, true, true, 5));

    /**
     * Backup source preset profile.
     */
    public static final SyncProfile BACKUP_SOURCE = new SyncProfile(
            "backupsource", false,
            new SyncProfileConfiguration(false, false, false, false, 5));

    /**
     * Backup target preset profile.
     */
    public static final SyncProfile BACKUP_TARGET = new SyncProfile(
            "backuptarget", false,
            new SyncProfileConfiguration(true, true, true, true, 60));

    /**
     * Project work preset profile.
     */
    public static final SyncProfile PROJECT_WORK = new SyncProfile(
            "projectwork", false,
            new SyncProfileConfiguration(false, false, false, false, 0));

    // All preset sync profiles
    private static final SyncProfile[] PRESET_SYNC_PROFILES = new SyncProfile[]{
        SYNCHRONIZE_PCS, BACKUP_SOURCE, BACKUP_TARGET, AUTO_DOWNLOAD_FROM_ALL,
        MANUAL_DOWNLOAD, PROJECT_WORK};

    /** Migration for #603 */
    public static final SyncProfile AUTO_DOWNLOAD_FRIENDS = new SyncProfile(
            "autodownload_friends", false,
            new SyncProfileConfiguration(true, true, true, false, 30));

    /** Special no-sync profile for preview folders. Same config as PROJECT_WORK */
    public static final SyncProfile NO_SYNC = new SyncProfile(
            "no_sync", false,
            new SyncProfileConfiguration(false, false, false, false, 0));
    
    /** Special no-sync profile for disabled folders in Online Storage. Only syncs file deletions */
    public static final SyncProfile DISABLED = new SyncProfile(
           "disabled", false,
            new SyncProfileConfiguration(false, false, true, true, 0));

    /**
     * All custom profiles
     */
    private static final List<SyncProfile> customProfiles =
            new ArrayList<SyncProfile>();

    /**
     * The name of the profile (for custom profiles)
     */
    private String profileName;

    /**
     * The id of the profile (for preset profiles)
     */
    private String profileId;

    /**
     * Indicates that this is a custom profile. This should only ever be false
     * for the static preset profiles created inside this class.
     */
    private final boolean custom;

    /**
     * The internal configuration of the profile.
     * This determines how a folder synchronizes with other nodes.
     */
    private SyncProfileConfiguration configuration;

    /**
     * Constructor.
     *
     * @param profileNameId name (custom) or id (preset) of the profile
     * @param custom whether this is a custom profile
     * @param configuration the configuration of the profile
     */
    private SyncProfile(String profileNameId, boolean custom,
                        SyncProfileConfiguration configuration) {
        if (custom) {
            profileName = profileNameId;
        } else {
            profileId = profileNameId;
        }
        this.custom = custom;
        this.configuration = configuration;
    }

    /**
     * Returns tue if this is a custom profile.
     *
     * @return
     */
    public boolean isCustom() {
        return custom;
    }

    /**
     * Returns the profile name.
     *
     * @return
     */
    public String getProfileName() {
        if (custom) {
            return profileName;
        } else {
            return translateId(profileId);
        }
    }

    /**
     * Sets the profile name. It is illegal to set the profileName the same as
     * another profile, because this breaks the required uniquness of profiles.
     * Always test for name uniqueness first with the safe checkName() method.
     *
     * @param profileName
     * @see #checkName(String)
     */
    public void setProfileName(String profileName) {

        Reject.ifFalse(custom, "Cannot set the profileName of preset profile " +
                getProfileName() + " to " + profileName);
        Reject.ifBlank(profileName, "ProfileName not supplied");

        // Ensure that the name is not being set to an existing sync profile name
        for (SyncProfile profile : PRESET_SYNC_PROFILES) {
            if (!equals(profile) && profile.getProfileName().equals(profileName)) {
                throw new RuntimeException("Preset profile name already exists.");
            }
        }
        synchronized (customProfiles) {
            for (SyncProfile customProfile : customProfiles) {
                if (!equals(customProfile) &&
                        customProfile.getProfileName().equals(profileName)) {
                    throw new RuntimeException("Custom profile name already exists.");
                }
            }
        }

        String oldProfileName = this.profileName;
        this.profileName = profileName;
        if (logVerbose) {
            getLogger().verbose("Set profile name from " + oldProfileName +
                    " to " + profileName);
        }
    }

    public SyncProfileConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SyncProfileConfiguration configuration) {

        Reject.ifFalse(custom,
                "Cannot set the configuration of preset profile " +
                getProfileName());
        Reject.ifNull(configuration, "configuration not supplied");

        // Ensure that the config is unique
        for (SyncProfile profile : PRESET_SYNC_PROFILES) {
            if (!equals(profile) && profile.configuration.equals(configuration)) {
                throw new RuntimeException("Preset profile config already exists.");
            }
        }
        synchronized (customProfiles) {
            for (SyncProfile customProfile : customProfiles) {
                if (!equals(customProfile) &&
                        customProfile.configuration.equals(configuration)) {
                    throw new RuntimeException("Custom profile config already exists.");
                }
            }
        }

        SyncProfileConfiguration oldConfiguration = this.configuration;
        this.configuration = configuration;
        if (logVerbose) {
            getLogger().verbose("Set configuration from " +
                    oldConfiguration.toString() + " to " +
                    configuration.toString());
        }
    }

    /**
     * This is used to persist profiles to the configuration. NOTE: Existing
     * sync profiles may not load if this is changed. Add any new fields to the
     * end of the list.
     *
     * @return string representation of the profile config as a list of fields
     */
    public String getFieldList() {
        return configuration.isAutoDownloadFromFriends() +
                FIELD_LIST_DELIMITER +
                configuration.isAutoDownloadFromOthers() +
                FIELD_LIST_DELIMITER +
                configuration.isSyncDeletionWithFriends() +
                FIELD_LIST_DELIMITER +
                configuration.isSyncDeletionWithOthers() +
                FIELD_LIST_DELIMITER +
                configuration.getTimeBetweenRegularScans() +
                FIELD_LIST_DELIMITER +
                configuration.isDailySync() +
                FIELD_LIST_DELIMITER +
                configuration.getDailyHour() +
                FIELD_LIST_DELIMITER +
                configuration.getDailyDay() +
                FIELD_LIST_DELIMITER +
                configuration.getRegularTimeType() +
                FIELD_LIST_DELIMITER +
                getProfileName();
    }

    /**
     * For preset config, the name is i18n using 'syncprofile.x.name'.
     *
     * @param id translate 'syncprofile.[id].name'
     * @param silent silent translation so warnings are not logged - don't care
     * @return
     */
    private static String translateId(String id) {
        return Translation.getTranslation("syncprofile." + id + ".name");
    }

    /**
     * Method for either retrieving or creating a sync profile from the
     * caches. Note that if a profile is retrieved, it may not have the same
     * name as the profileNameArg arg, but it will have the same configuration.
     *
     */
    public static SyncProfile retrieveSyncProfile(String profileNameArg,
                                                  SyncProfileConfiguration syncProfileConfigurationArg)
    {

        Reject.ifNull(syncProfileConfigurationArg, "Null sync profile configuration");

        List<String> names = new ArrayList<String>();

        // Check presetProfiles
        for (SyncProfile profile : PRESET_SYNC_PROFILES) {
            if (profile.configuration
                    .equals(syncProfileConfigurationArg)) {

                return profile;
            }
            names.add(profile.getProfileName());
        }

        // Check existing profiles
        synchronized (customProfiles) {
            for (SyncProfile customProfile : customProfiles) {
                if (customProfile.configuration
                        .equals(syncProfileConfigurationArg)) {
                    return customProfile;
                }
                names.add(customProfile.getProfileName());
            }
        }

        // Ensure new profile has a unique name;
        boolean emptyName = profileNameArg.trim().equals("");
        String workingProfileName = emptyName ? translateId("custom") :
                profileNameArg;
        SyncProfile syncProfile;
        if (names.contains(workingProfileName) || emptyName) {
            int i = 1;
            while (names.contains(workingProfileName + ' ' + i)) {
                i++;
            }
            syncProfile = new SyncProfile(
                    workingProfileName + ' ' + i, true,
                    syncProfileConfigurationArg);
        } else {
            syncProfile = new SyncProfile(workingProfileName, true,
                    syncProfileConfigurationArg);
        }

        // Store in the custom cache.
        synchronized (customProfiles) {
            customProfiles.add(syncProfile);
        }

        return syncProfile;
    }

    /**
     * Gets a copy of the sync profiles. Adding or deleting from this list does
     * not affect the SyncProfile caches, but changing the profile config does.
     *
     * @return Shallow copy of SyncProfile caches.
     */
    public static List<SyncProfile> getSyncProfilesCopy() {
        List<SyncProfile> list = new ArrayList<SyncProfile>();
        list.addAll(Arrays.asList(PRESET_SYNC_PROFILES));
        synchronized (customProfiles) {
            list.addAll(customProfiles);
        }
        return list;
    }

    /**
     * Tries to resolve a sync profile by id (the old way of storing sync
     * profiles). Else it expects a comma-separated list of profile fieldList.
     *
     * @param fieldList
     * @return
     * @see #getFieldList()
     */
    public static SyncProfile getSyncProfileByFieldList(String fieldList) {

        Reject.ifNull(fieldList, "Null sync profile fieldList");

        // Old way was to store the SyncProfile's id. search presets
        if (!fieldList.contains(FIELD_LIST_DELIMITER)) {
            for (SyncProfile syncProfile : PRESET_SYNC_PROFILES) {
                if (fieldList.equals(syncProfile.profileId)) {
                    return syncProfile;
                }
            }
        }

        // Preferred way is to store the sync profile as its getFieldList().
        // This allows for custom profiles.
        StringTokenizer st = new StringTokenizer(fieldList, FIELD_LIST_DELIMITER);
        boolean autoDownloadFromFriends = false;
        if (st.hasMoreTokens()) {
            autoDownloadFromFriends = Boolean.parseBoolean(st.nextToken());
        }
        boolean autoDownloadFromOthers = false;
        if (st.hasMoreTokens()) {
            autoDownloadFromOthers = Boolean.parseBoolean(st.nextToken());
        }
        boolean syncDeletionWithFriends = false;
        if (st.hasMoreTokens()) {
            syncDeletionWithFriends = Boolean.parseBoolean(st.nextToken());
        }
        boolean syncDeletionWithOthers = false;
        if (st.hasMoreTokens()) {
            syncDeletionWithOthers = Boolean.parseBoolean(st.nextToken());
        }
        int timeBetweenScans = 0;
        if (st.hasMoreTokens()) {
            timeBetweenScans = Integer.parseInt(st.nextToken());
        }
        boolean dailySync = false;
        if (st.hasMoreTokens()) {
            dailySync = Boolean.parseBoolean(st.nextToken());
        }
        int dailyHour = SyncProfileConfiguration.DAILY_HOUR_DEFAULT;
        if (st.hasMoreTokens()) {
            dailyHour = Integer.parseInt(st.nextToken());
        }
        int dailyDay = SyncProfileConfiguration.DAILY_DAY_EVERY_DAY;
        if (st.hasMoreTokens()) {
            dailyDay = Integer.parseInt(st.nextToken());
        }
        String timeType = SyncProfileConfiguration.REGULAR_TIME_TYPE_MINUTES;
        if (st.hasMoreTokens()) {
            timeType = st.nextToken();
        }
        String profileName = "";
        if (st.hasMoreTokens()) {
            profileName = st.nextToken();
        }

        return retrieveSyncProfile(profileName,
                new SyncProfileConfiguration(autoDownloadFromFriends,
                        autoDownloadFromOthers, syncDeletionWithFriends,
                        syncDeletionWithOthers, timeBetweenScans, dailySync,
                        dailyHour, dailyDay, timeType));
    }

    /**
     * If folder automatically detects changes to files on disk
     *
     * @return
     */
    public boolean isAutoDetectLocalChanges() {
        return configuration.getTimeBetweenRegularScans() > 0;
    }

    /**
     * Answers the seconds to wait between disk scans. Only relevant if
     * auto-detect changes is enabled
     *
     * @return
     */
    public int getSecondsBetweenScans() {
        String timeType = configuration.getRegularTimeType();
        if (configuration.getRegularTimeType() == null) {
            timeType = SyncProfileConfiguration.REGULAR_TIME_TYPE_MINUTES;
        }
        if (SyncProfileConfiguration.REGULAR_TIME_TYPE_SECONDS
                .equals(timeType)) {
            return configuration.getTimeBetweenRegularScans();
        } else if (SyncProfileConfiguration.REGULAR_TIME_TYPE_HOURS
                .equals(timeType)) {
            return configuration.getTimeBetweenRegularScans() * 3600;
        } else {
            return configuration.getTimeBetweenRegularScans() * 60;
        }
    }

    /**
     * Convinience method. Anwers if autodownload is enabled (from friends or
     * others)
     *
     * @return
     */
    public boolean isAutodownload() {
        return configuration.isAutoDownloadFromFriends() ||
                configuration.isAutoDownloadFromOthers();
    }

    /**
     * @return true if syncing deletions with any other user
     */
    public boolean isSyncDeletion() {
        return configuration.isSyncDeletionWithFriends() ||
                configuration.isSyncDeletionWithOthers();
    }

    /**
     * Remove a profile from the cache.
     *
     * @param profileArg
     */
    public static void deleteProfile(SyncProfile profileArg) {
        synchronized (customProfiles) {
            for (Iterator<SyncProfile> iter = customProfiles.iterator(); iter.hasNext();) {
                SyncProfile profile = iter.next();
                if (profile.equals(profileArg)) {
                    iter.remove();
                }
            }
        }
    }
}