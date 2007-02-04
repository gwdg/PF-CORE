package de.dal33t.powerfolder.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.TimerTask;

import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.event.TransferManagerEvent;
import de.dal33t.powerfolder.event.TransferManagerListener;
import de.dal33t.powerfolder.net.ConnectionListener;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.ComplexComponentFactory;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.HasUIPanel;
import de.dal33t.powerfolder.util.ui.LimitedConnectivityChecker;

/**
 * The status bar on the lower side of the main window.
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class StatusBar extends PFUIComponent implements HasUIPanel {
    private Component comp;

    /** Online state info field */
    private JLabel onlineStateInfo, limitedConnectivityLabel, syncLabel, upStats,
        downStats, portLabel;

    protected StatusBar(Controller controller) {
        super(controller);
    }

    public Component getUIComponent() {
        if (comp == null) {
            int col = 1;
            boolean showPort = ConfigurationEntry.NET_BIND_RANDOM_PORT
                .getValueBoolean(getController())
                && getController().getConnectionListener().getPort() != ConnectionListener.DEFAULT_PORT;
            initComponents();

            FormLayout layout;
            if (showPort) {
            	layout = new FormLayout(
            			"pref, 3dlu, pref, 3dlu, pref, fill:pref:grow, pref, 3dlu, pref, 3dlu, pref, 3dlu, pref",
            			"pref");
            } else {
            	layout = new FormLayout(
            			"pref, 3dlu, pref, 3dlu, pref, fill:pref:grow, pref, 3dlu, pref, 3dlu, pref",
            			"pref");
            }
            DefaultFormBuilder b = new DefaultFormBuilder(layout);
            b.setBorder(Borders.createEmptyBorder("0, 1dlu, 0, 2dlu"));

            CellConstraints cc = new CellConstraints();
            b.add(onlineStateInfo, cc.xy(col, 1));
            col += 2;
            
            b.add(syncLabel, cc.xy(col, 1));
            col += 2;
            
            b.add(limitedConnectivityLabel, cc.xy(col, 1));
            col += 2;

            if (showPort) {
            	b.add(portLabel, cc.xy(col, 1));
                col += 2;
            }
            
            JSeparator sep1 = new JSeparator(SwingConstants.VERTICAL);
            sep1.setPreferredSize(new Dimension(2, 12));
            
            b.add(downStats, cc.xy(col, 1));
            col += 2;
            b.add(sep1, cc.xy(col, 1));
            col += 2;
            b.add(upStats, cc.xy(col, 1));
            return b.getPanel();
        }
        return comp;
    }

    private void initComponents() {
        // Create online state info
        onlineStateInfo = ComplexComponentFactory
            .createOnlineStateLabel(getController());
        // Add behavior
        onlineStateInfo.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                // open connect dialog
                getUIController().getConnectAction().actionPerformed(null);
            }
        });

        upStats = ComplexComponentFactory
            .createTransferCounterLabel(getController(), Translation
                .getTranslation("status.upload"), getController()
                .getTransferManager().getTotalUploadTrafficCounter());

        downStats = ComplexComponentFactory.createTransferCounterLabel(
            getController(), Translation.getTranslation("status.download"),
            getController().getTransferManager()
                .getTotalDownloadTrafficCounter());

        limitedConnectivityLabel = new JLabel();
        limitedConnectivityLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (getController().hasLimitedConnectivity()) {
                    DialogFactory.showWarningDialog(getUIController()
                        .getMainFrame().getUIComponent(), Translation
                        .getTranslation("limitedconnection.title"), Translation
                        .getTranslation("limitedconnection.text"));
                }
            }
        });
        getController().scheduleAndRepeat(new MyConnectivityChecker(),
            LimitedConnectivityChecker.TEST_CONNECTIVITY_DELAY * 1000,
            LimitedConnectivityChecker.TEST_CONNECTIVITY_DELAY * 500);

        syncLabel = new JLabel();
        getController().getTransferManager().addListener(
            new MyTransferManagerListener());
        updateSyncLabel();
        
        portLabel = new JLabel(Translation.getTranslation("status.port", 
        		getController().getConnectionListener().getPort()));
    }

    private void updateSyncLabel() {
        if (getController().getFolderRepository().isAnyFolderSyncing()) {
            syncLabel.setText(Translation.getTranslation("statusbar.synchronizing"));
            syncLabel.setIcon(Icons.DOWNLOAD_ACTIVE);
        } else {
            syncLabel.setText(null);
            syncLabel.setIcon(null);
        }
    }

    private class MyConnectivityChecker extends TimerTask {
        @Override
        public void run()
        {
            if (getController().hasLimitedConnectivity()) {
                limitedConnectivityLabel.setText(Translation
                    .getTranslation("limitedconnection.title"));
                limitedConnectivityLabel.setIcon(Icons.WARNING);
            } else {
                limitedConnectivityLabel.setText("");
                limitedConnectivityLabel.setIcon(null);
            }
        }
    }
    
    private class MyTransferManagerListener implements TransferManagerListener {

        public void completedDownloadRemoved(TransferManagerEvent event) { 
        }

        public void downloadAborted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void downloadBroken(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void downloadCompleted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void downloadQueued(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void downloadRequested(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void downloadStarted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void pendingDownloadEnqueud(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void uploadAborted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void uploadBroken(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void uploadCompleted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void uploadRequested(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public void uploadStarted(TransferManagerEvent event) {
            updateSyncLabel();
        }

        public boolean fireInEventDispathThread() {
            return true;
        }
        
    }
}
