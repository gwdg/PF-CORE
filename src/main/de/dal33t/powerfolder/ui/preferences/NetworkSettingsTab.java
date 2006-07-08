package de.dal33t.powerfolder.ui.preferences;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

import javax.swing.*;

import org.apache.commons.lang.StringUtils;

import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.NetworkingMode;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.LineSpeedSelectionPanel;
import de.dal33t.powerfolder.util.ui.LinkLabel;
import de.dal33t.powerfolder.util.ui.SimpleComponentFactory;

public class NetworkSettingsTab extends PFComponent implements PreferenceTab {
    private static final int PRIVATE_MODE_INDEX = 0;
    private static final int PUBLIC_MODE_INDEX = 1;
    private static final int LANONLY_MODE_INDEX = 2;

    private JPanel panel;
    private JComboBox networkingMode;
    private JLabel myDnsLabel;
    private JTextField myDnsField;
    private ValueModel mydnsndsModel;
    private LineSpeedSelectionPanel wanSpeed;
    private LineSpeedSelectionPanel lanSpeed;
    private JSlider silentModeThrottle;
    boolean needsRestart = false;
    private JLabel silentThrottleLabel;

    public NetworkSettingsTab(Controller controller, ValueModel mydnsndsModel) {
        super(controller);
        this.mydnsndsModel = mydnsndsModel;
        initComponents();
    }

    public String getTabName() {
        return Translation.getTranslation("preferences.dialog.network.title");
    }

    public boolean needsRestart() {
        return needsRestart;
    }

    public void undoChanges() {

    }

    public boolean validate() {
        return true;
    }

    private void initComponents() {
        String[] options = new String[3];
        options[PRIVATE_MODE_INDEX] = Translation
            .getTranslation("preferences.dialog.networkmode.private");
        options[PUBLIC_MODE_INDEX] = Translation
            .getTranslation("preferences.dialog.networkmode.public");

        options[LANONLY_MODE_INDEX] = Translation
            .getTranslation("preferences.dialog.networkmode.lanonly");
        networkingMode = new JComboBox(options);
        if (getController().isLanOnly()) {
            networkingMode.setSelectedIndex(LANONLY_MODE_INDEX);
            networkingMode
                .setToolTipText(Translation
                    .getTranslation("preferences.dialog.networkmode.lanonly.tooltip"));
        } else if (getController().isPublicNetworking()) {
            networkingMode.setSelectedIndex(PUBLIC_MODE_INDEX);
            networkingMode
                .setToolTipText(Translation
                    .getTranslation("preferences.dialog.networkmode.public.tooltip"));
        } else { // private
            networkingMode.setSelectedIndex(PRIVATE_MODE_INDEX);
            networkingMode
                .setToolTipText(Translation
                    .getTranslation("preferences.dialog.networkmode.private.tooltip"));
        }

        networkingMode.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String tooltip = null;
                switch (networkingMode.getSelectedIndex()) {
                    case PRIVATE_MODE_INDEX : {
                        tooltip = Translation
                            .getTranslation("preferences.dialog.networkmode.private.tooltip");
                        break;
                    }
                    case PUBLIC_MODE_INDEX : {
                        tooltip = Translation
                            .getTranslation("preferences.dialog.networkmode.public.tooltip");
                        break;
                    }
                    case LANONLY_MODE_INDEX : {
                        tooltip = Translation
                            .getTranslation("preferences.dialog.networkmode.lanonly.tooltip");
                        break;
                    }
                }
                networkingMode.setToolTipText(tooltip);

            }

        });

        // DynDns
        myDnsLabel = new LinkLabel(Translation
            .getTranslation("preferences.dialog.dyndns"), Translation
            .getTranslation("preferences.dialog.dyndns.link"));

        myDnsField = SimpleComponentFactory
            .createTextField(mydnsndsModel, true);

        wanSpeed = new LineSpeedSelectionPanel();
        wanSpeed.loadWANSelection();
        wanSpeed.setUploadSpeedKBPS(getController().getTransferManager()
            .getAllowedUploadCPSForWAN() / 1024);

        lanSpeed = new LineSpeedSelectionPanel();
        lanSpeed.loadLANSelection();
        lanSpeed.setUploadSpeedKBPS(getController().getTransferManager()
            .getAllowedUploadCPSForLAN() / 1024);

        silentThrottleLabel = new JLabel(Translation
            .getTranslation("preferences.dialog.silentthrottle"));
        silentThrottleLabel.setToolTipText(Translation
            .getTranslation("preferences.dialog.silentthrottle.tooltip"));

        silentModeThrottle = new JSlider();
        silentModeThrottle.setMajorTickSpacing(25);
        silentModeThrottle.setMinorTickSpacing(5);

        silentModeThrottle.setPaintTicks(true);
        silentModeThrottle.setPaintLabels(true);
        int smt = 70;
        try {
            smt = Integer
                .parseInt(ConfigurationEntry.UPLOADLIMIT_SILENTMODE_THROTTLE
                    .getValue(getController()));
        } catch (NumberFormatException e) {
            log().debug("silentmodethrottle" + e);
        }
        silentModeThrottle.setValue(smt);
    }

    /**
     * Creates the JPanel for advanced settings
     * 
     * @return the created panel
     */
    public JPanel getUIPanel() {
        if (panel == null) {
            FormLayout layout = new FormLayout(
                "right:100dlu, 7dlu, 30dlu, 3dlu, 15dlu, 10dlu, 30dlu, 30dlu, pref",
                "pref, 3dlu, pref, 3dlu, pref, 3dlu, top:pref, 3dlu, top:pref, 3dlu, top:pref:grow, 3dlu");
            PanelBuilder builder = new PanelBuilder(layout);
            builder.setBorder(Borders
                .createEmptyBorder("3dlu, 0dlu, 0dlu, 0dlu"));
            CellConstraints cc = new CellConstraints();

            int row = 1;
            builder.add(networkingMode, cc.xywh(3, row, 7, 1));

            row += 2;
            builder.add(myDnsLabel, cc.xy(1, row));
            builder.add(myDnsField, cc.xywh(3, row, 7, 1));

            row += 2;
            builder.add(new JLabel(Translation
                .getTranslation("preferences.dialog.linesettings")), cc.xy(1,
                row));
            builder.add(wanSpeed, cc.xywh(3, row, 7, 1));

            row += 2;
            builder.add(new JLabel(Translation
                .getTranslation("preferences.dialog.lanlinesettings")), cc.xy(
                1, row));
            builder.add(lanSpeed, cc.xywh(3, row, 7, 1));

            row += 2;
            // TODO BYTEKEEPER Please don't mix initalization
            // and panel building. Create a private field for this
            // JLabel and initalize it in initComponents.
            builder.add(silentThrottleLabel, cc.xy(1, row));
            builder.add(silentModeThrottle, cc.xywh(3, row, 7, 1));
            panel = builder.getPanel();
        }
        return panel;
    }

    /**
     * Saves the network settings.
     */
    public void save() {
        NetworkingMode netMode;
        switch (networkingMode.getSelectedIndex()) {
            case 0 : {
                netMode = NetworkingMode.PRIVATEMODE;
                break;
            }
            case 1 : {
                netMode = NetworkingMode.PUBLICMODE;
                break;
            }
            case 2 : {
                netMode = NetworkingMode.LANONLYMODE;
                break;
            }
            default :
                throw new IllegalStateException("invalid index");
        }
        getController().setNetworkingMode(netMode);
        getController().getTransferManager().setAllowedUploadCPSForWAN(
            wanSpeed.getUploadSpeedKBPS());
        getController().getTransferManager().setAllowedUploadCPSForLAN(
            lanSpeed.getUploadSpeedKBPS());
        String dyndnsHost = (String) mydnsndsModel.getValue();
        // remove the dyndns, this is done here because
        // the save method of "invisible" tabs are not called
        // and if the mydnsndsModel is empty the dyndns tab is "invisible"
        if (StringUtils.isBlank(dyndnsHost)) {
            ConfigurationEntry.DYNDNS_HOSTNAME.removeValue(getController());
        }
        ConfigurationEntry.UPLOADLIMIT_SILENTMODE_THROTTLE.setValue(
            getController(), Integer.toString(silentModeThrottle.getValue()));
    }
}
