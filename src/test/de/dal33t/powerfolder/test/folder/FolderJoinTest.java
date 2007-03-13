/* $Id: FolderJoinTest.java,v 1.2 2006/04/16 23:01:52 totmacherr Exp $
 */
package de.dal33t.powerfolder.test.folder;

import java.io.File;

import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderException;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.test.TestHelper;
import de.dal33t.powerfolder.test.TwoControllerTestCase;
import de.dal33t.powerfolder.util.IdGenerator;

/**
 * Tests if both instance join the same folder by folder id
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.2 $
 */
public class FolderJoinTest extends TwoControllerTestCase {

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        connectBartAndLisa();
    }

    public void testJoinSecretFolder() {
        // Join on testfolder
        FolderInfo testFolder = new FolderInfo("testFolder", IdGenerator
            .makeId(), true);
        joinFolder(testFolder, TESTFOLDER_BASEDIR_BART, TESTFOLDER_BASEDIR_LISA);

        assertEquals(2, getContollerBart().getFolderRepository().getFolder(
            testFolder).getMembersCount());
        assertEquals(2, getContollerLisa().getFolderRepository().getFolder(
            testFolder).getMembersCount());
    }

    public void testJoinPublicFolder() {
        // Join on testfolder
        FolderInfo testFolder = new FolderInfo("testFolder", IdGenerator
            .makeId(), false);
        joinFolder(testFolder, TESTFOLDER_BASEDIR_BART, TESTFOLDER_BASEDIR_LISA);

        assertEquals(2, getContollerBart().getFolderRepository().getFolder(
            testFolder).getMembersCount());
        assertEquals(2, getContollerLisa().getFolderRepository().getFolder(
            testFolder).getMembersCount());
    }

    public void testJoinMultipleFolders() {
        for (int i = 0; i < 10; i++) {
            FolderInfo testFolder = createRandomFolder("s-" + i, true);
            File folderDirBart = new File(TESTFOLDER_BASEDIR_BART,
                testFolder.name);
            File folderDirLisa = new File(TESTFOLDER_BASEDIR_LISA,
                testFolder.name);
            joinFolder(testFolder, folderDirBart, folderDirLisa);
        }

        for (int i = 0; i < 10; i++) {
            FolderInfo testFolder = createRandomFolder("p-" + i, false);
            File folderDirBart = new File(TESTFOLDER_BASEDIR_BART,
                testFolder.name);
            File folderDirLisa = new File(TESTFOLDER_BASEDIR_LISA,
                testFolder.name);
            System.err.println("Joining folder: " + testFolder);
            joinFolder(testFolder, folderDirBart, folderDirLisa);
        }

        Folder[] bartsFolders = getContollerBart().getFolderRepository()
            .getFolders();
        Folder[] lisasFolders = getContollerLisa().getFolderRepository()
            .getFolders();

        assertEquals(20, getContollerBart().getFolderRepository()
            .getFoldersCount());
        assertEquals(20, getContollerLisa().getFolderRepository()
            .getFoldersCount());
        assertEquals(20, bartsFolders.length);
        assertEquals(20, lisasFolders.length);

        for (Folder folder : lisasFolders) {
            assertEquals(2, folder.getMembersCount());
        }

        for (Folder folder : bartsFolders) {
            assertEquals(2, folder.getMembersCount());
        }
    }

    private FolderInfo createRandomFolder(String nameSuffix, boolean secret) {
        String folderName = "testFolder-" + nameSuffix;
        return new FolderInfo(folderName, IdGenerator.makeId(), secret);
    }

    /**
     * Test the download starting after joined a folder with auto-download.
     * <p>
     * Trac #19
     * 
     * @throws FolderException
     * @throws IOException
     */
    public void testStartAutoDownload() throws FolderException {
        FolderInfo testFolder = new FolderInfo("testFolder", IdGenerator
            .makeId(), true);

        // Prepare folder on "host" Bart.
        TestHelper.createRandomFile(TESTFOLDER_BASEDIR_BART);
        TestHelper.createRandomFile(TESTFOLDER_BASEDIR_BART);
        TestHelper.createRandomFile(TESTFOLDER_BASEDIR_BART);

        getContollerBart().getFolderRepository().createFolder(testFolder,
            TESTFOLDER_BASEDIR_BART, SyncProfile.MANUAL_DOWNLOAD, false);

        // Now let lisa join with auto-download
        final Folder folderLisa = getContollerLisa().getFolderRepository()
            .createFolder(testFolder, TESTFOLDER_BASEDIR_LISA,
                SyncProfile.AUTO_DOWNLOAD_FROM_ALL, false);

        TestHelper.waitForCondition(20, new TestHelper.Condition() {
            public boolean reached() {
                return folderLisa.getFiles().length >= 3;
            }
        });

        assertEquals(3, folderLisa.getFilesCount());
        assertEquals(4, folderLisa.getLocalBase().list().length);
    }

    /**
     * Test the download starting after joined a folder with auto-download.
     * <p>
     * Trac #19
     * 
     * @throws FolderException
     * @throws IOException
     */
    public void testStartAutoDownloadInSilentMode() throws FolderException {
        FolderInfo testFolder = new FolderInfo("testFolder", IdGenerator
            .makeId(), true);
        // Prepare folder on "host" Bart.
        Folder folderBart = getContollerBart().getFolderRepository()
            .createFolder(testFolder, TESTFOLDER_BASEDIR_BART,
                SyncProfile.MANUAL_DOWNLOAD, false);

        TestHelper.createRandomFile(folderBart.getLocalBase());
        TestHelper.createRandomFile(folderBart.getLocalBase());
        TestHelper.createRandomFile(folderBart.getLocalBase());
        folderBart.forceScanOnNextMaintenance();
        folderBart.maintain();

        // Set lisa in silent mode
        getContollerLisa().setSilentMode(true);

        // Now let lisa join with auto-download
        final Folder folderLisa = getContollerLisa().getFolderRepository()
            .createFolder(testFolder, TESTFOLDER_BASEDIR_LISA,
                SyncProfile.AUTO_DOWNLOAD_FROM_ALL, false);

        TestHelper.waitForCondition(50, new TestHelper.Condition() {
            public boolean reached() {
                return folderLisa.getFiles().length >= 3;
            }
        });

        assertEquals(3, folderLisa.getFilesCount());
        assertEquals(4, folderLisa.getLocalBase().list().length);
    }
}
