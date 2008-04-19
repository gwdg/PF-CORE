package de.dal33t.powerfolder.ui.myfolders;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.jgoodies.forms.factories.Borders;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderStatistic;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.render.UnsortedTableHeaderRenderer;
import de.dal33t.powerfolder.ui.action.FolderRemoveAction;
import de.dal33t.powerfolder.ui.action.PreviewJoinAction;
import de.dal33t.powerfolder.ui.builder.ContentPanelBuilder;
import de.dal33t.powerfolder.ui.model.FoldersTableModel;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.PFUIPanel;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.CustomTableHelper;
import de.dal33t.powerfolder.util.ui.CustomTableModel;
import de.dal33t.powerfolder.util.ui.DoubleClickAction;
import de.dal33t.powerfolder.util.ui.PopupMenuOpener;
import de.dal33t.powerfolder.util.ui.SelectionModel;
import de.dal33t.powerfolder.util.ui.SyncProfileUtil;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * Shows a Table with the Folders that are "joined" and toolbar. Uses a table
 * that allows hiding of columns.<BR>
 * TODO: add focus lost listener to SyncStyles editor to stop editing, this is
 * partialy fixed, but focus lost is not fired if clicked on empty space...<BR>
 * TODO: make some columns CustomTableModel required (not allowed to hide) <BR>
 * TODO: maybe sync% should be a bar.<BR>
 * TODO: colors for deleted etc.<BR>
 * 
 * @author <A HREF="mailto:schaatser@powerfolder.com">Jan van Oosterom</A>
 * @version $Revision: 1.3 $
 */
public class MyFoldersPanel extends PFUIPanel {
    private JComponent panel;

    private MyFoldersQuickInfoPanel quickinfo;
    private MyFoldersTable table;
    private JScrollPane tablePane;
    private JPanel toolbar;
    private SelectionModel selectionModel;
    private CustomTableModel customTableModel;
    private FoldersTableModel myFoldersTableModel;
    private DefaultCellEditor syncProfileEditor;

    public MyFoldersPanel(Controller controller) {
        super(controller);
        selectionModel = new SelectionModel();
    }

    public Component getUIComponent() {
        if (panel == null) {
            initComponents();
            ContentPanelBuilder builder = new ContentPanelBuilder();
            builder.setQuickInfo(quickinfo.getUIComponent());
            builder.setToolbar(toolbar);
            builder.setContent(tablePane);
            panel = builder.getPanel();
        }
        return panel;
    }

    public String getTitle() {
        return Translation.getTranslation("title.my.folders");
    }

    private void initComponents() {
        quickinfo = new MyFoldersQuickInfoPanel(getController());

        myFoldersTableModel = getUIController().getFolderRepositoryModel()
            .getMyFoldersTableModel();
        customTableModel = new CustomTableModel(myFoldersTableModel);
        table = new MyFoldersTable(customTableModel);

        table.getSelectionModel().addListSelectionListener(
            new TableSelectionListener());
        table.setDefaultRenderer(Folder.class, new MyFolderTableCellRenderer());
        // editor for sync profiles:
        JComboBox comboBox = new SyncProfileJComboBox(SyncProfile
            .getSyncProfilesCopy().toArray());
        comboBox.setRenderer(new SyncProfileComboBoxRenderer());

        comboBox.addFocusListener(new SyncProfileFocusListener());
        syncProfileEditor = new DefaultCellEditor(comboBox);
        table.setDefaultEditor(Folder.class, syncProfileEditor);
        table.addMouseListener(new DoubleClickAction(new OpenFolderAction()));

        tablePane = new JScrollPane(table);
        UIUtil.whiteStripTable(table);
        UIUtil.removeBorder(tablePane);
        UIUtil.setZeroHeight(tablePane);

        boolean[] defaults = myFoldersTableModel.getDefaultVisibilities();
        CustomTableHelper.setupFromPref(getController(), customTableModel,
            "myfolderstable", defaults);
        // customize the table popup menu
        JPopupMenu popup = CustomTableHelper.createSetUpColumnsMenu(
            getController(), customTableModel, "myfolderstable", table);
        UnsortedTableHeaderRenderer.associateHeaderRenderer(table
            .getColumnModel());
        // popup appears on the table header
        table.getTableHeader().addMouseListener(new PopupMenuOpener(popup));

        // Create toolbar
        toolbar = createToolBar();

    }

    /**
     * @return the toolbar
     */
    private JPanel createToolBar() {
        // Create toolbar
        JButton newWizardButton = new JButton(getUIController()
            .getOpenWizardAction());
        newWizardButton.setIcon(null);

        ButtonBarBuilder bar = ButtonBarBuilder.createLeftToRightBuilder();
        bar
            .addGridded(new JButton(getUIController().getSyncAllFoldersAction()));
        bar.addUnrelatedGap();
        bar.addGridded(newWizardButton);
        bar.addUnrelatedGap();
        bar.addGridded(new JButton(new FolderRemoveAction(getController(),
            selectionModel)));
        bar.addRelatedGap();
        bar.addGridded(new JButton(new PreviewJoinAction(getController(),
            selectionModel)));

        JPanel barPanel = bar.getPanel();
        barPanel.setBorder(Borders.DLU4_BORDER);

        return barPanel;
    }

    /** updates the selectionModel for the actions */
    private class TableSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                selectionModel.setSelection(null);
            } else {
                Folder folder = (Folder) customTableModel.getValueAt(
                    selectedRow, 0);
                selectionModel.setSelection(folder);
            }
        }
    }

    // renders the cell contents
    private class MyFolderTableCellRenderer extends DefaultTableCellRenderer {

        public Component getTableCellRendererComponent(JTable table1,
            Object value, boolean isSelected, boolean hasFocus, int row,
            int column)
        {
            TableColumnModel columns = table1.getColumnModel();
            TableColumn tableColumn = columns.getColumn(column);
            int currentColumnWidth = tableColumn.getWidth();
            Folder folder = (Folder) value;
            String newValue = "";
            setIcon(null);
            setToolTipText(null);
            FolderStatistic folderStatistic = folder.getStatistic();
            // default alignment, overwite if cell needs other alignment
            setHorizontalAlignment(SwingConstants.RIGHT);

            switch (customTableModel.mapToColumnIndex(UIUtil.toModel(table1,
                column))) {
                case 0 : { // Folder (name)
                    newValue = folder.getName();
                    setHorizontalAlignment(SwingConstants.LEFT);
                    setHorizontalTextPosition(SwingConstants.RIGHT);
                    setIcon(Icons.getIconFor(folder));
                    setToolTipText(newValue);
                    break;
                }
                case 1 : {// Sync % and Sync activity
                    double sync = folderStatistic.getHarmonizedSyncPercentage();
                    newValue = SyncProfileUtil.renderSyncPercentage(sync);
                    setIcon(SyncProfileUtil.getSyncIcon(sync));
                    setHorizontalTextPosition(SwingConstants.LEFT);
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    break;
                }
                case 2 : {// Sync profile
                    SyncProfile profile = folder.getSyncProfile();
                    newValue = profile.getProfileName();
                    setToolTipText(newValue);
                    break;
                }
                case 3 : {// Members
                    Member[] members = folder.getMembers();
                    String separetor = "";
                    for (int i = 0; i < members.length; i++) {
                        newValue += separetor + members[i].getNick();
                        separetor = ", ";
                    }
                    Component component = super.getTableCellRendererComponent(
                        table1, newValue, isSelected, hasFocus, row, column);
                    int prefWidth = component.getPreferredSize().width;
                    if (currentColumnWidth < prefWidth) {
                        newValue = Translation.getTranslation(
                            "myfolderstable.number_of_members", members.length
                                + "");
                    }
                    String toolTipValue = "<HTML><BODY>";
                    separetor = "";
                    for (int i = 0; i < members.length; i++) {
                        toolTipValue += separetor + members[i].getNick();
                        separetor = "<BR>";
                    }
                    toolTipValue += "</BODY></HTML>";
                    setToolTipText(toolTipValue);
                    break;
                }
                case 4 : {// #Local
                    newValue = folderStatistic.getLocalFilesCount() + "";
                    break;
                }
                case 5 : {// local size
                    newValue = Format.formatBytesShort(folderStatistic
                        .getSizeInSync(getController().getMySelf()))
                        + "";
                    break;
                }
                case 6 : {// #available
                    newValue = folderStatistic.getIncomingFilesCount() + "";
                    break;
                }
                case 7 : {// Total # Files
                    newValue = folderStatistic.getTotalFilesCount() + "";
                    break;
                }
                case 8 : {// total size
                    newValue = Format.formatBytesShort(folderStatistic
                        .getTotalSize())
                        + "";
                    break;
                }
            }
            return super.getTableCellRendererComponent(table1, newValue,
                isSelected, hasFocus, row, column);
        }
    }

    /** called if the SyncProfileEditor has lost its focus */
    private class SyncProfileFocusListener implements FocusListener {
        public void focusGained(FocusEvent e) {
        }

        /**
         * The editor should stop edit on focus lost this does not happen
         * automaticaly in Java ...., this fixes this partialy
         * 
         * @param e
         */
        public void focusLost(FocusEvent e) {
            syncProfileEditor.stopCellEditing();
        }

    }

    /** The ComboBox to use as editor for the SyncProfile */
    private class SyncProfileJComboBox extends JComboBox implements
        ListSelectionListener
    {
        public SyncProfileJComboBox(Object[] items) {
            super(items);
            table.getSelectionModel().addListSelectionListener(this);
        }

        // an other row has been selected change the selected SyncProfile
        public void valueChanged(ListSelectionEvent e) {
            int rowIndex = table.getSelectedRow();
            if (rowIndex != -1) {
                Folder folder = (Folder) customTableModel.getValueAt(rowIndex,
                    0);
                SyncProfile profile = folder.getSyncProfile();
                setSelectedItem(profile);
            }
        }
    }

    /** renders the items in the SyncProfile combobox */
    private class SyncProfileComboBoxRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
        {
            SyncProfile profile = (SyncProfile) value;
            // this realy messes things up:!
            // setHorizontalAlignment(SwingConstants.RIGHT);

            String newValue = profile.getProfileName();
            return super.getListCellRendererComponent(list, newValue, index,
                isSelected, cellHasFocus);
        }
    }

    /** on double click open the folder */
    private class OpenFolderAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                Folder folder = (Folder) customTableModel.getValueAt(
                    selectedRow, 0);
                getUIController().getControlQuarter().setSelected(folder);
            }
        }
    }
}
