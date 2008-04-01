package de.dal33t.powerfolder.ui.render;

import de.dal33t.powerfolder.ui.model.SortedTableModel;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Enumeration;

/**
 * Table header renderer for tables with models that support SortedTableModel.
 * Sets the header cell icon to a ^ or v icon if it is the sorted column.
 */
public class SortedTableHeaderRenderer extends JLabel implements TableCellRenderer {

    /**
     * Model that gives details of the sorder column and direction.
     */
    private SortedTableModel sortedTableModel;

    /**
     * Constructor. Associates with a SortedTableModel.
     *
     * @param sortedTableModel
     */
    public SortedTableHeaderRenderer(final SortedTableModel sortedTableModel,
                                     TableColumnModel columnModel,
                                     final int initialSortColumn) {
        this.sortedTableModel = sortedTableModel;

        // Associate columns with this renderer.
        Enumeration<TableColumn> columns = columnModel.getColumns();
        while (columns.hasMoreElements()) {
            columns.nextElement().setHeaderRenderer(this);
        }

        // Initialize the sorted data to match the headers. 
        Runnable r = new Runnable() {
            public void run() {
                sortedTableModel.sortBy(initialSortColumn);
            }
        };
        UIUtil.invokeLaterInEDT(r);
    }

    /**
     * This method is called each time a column header using this renderer
     * needs to be rendered. Implements the TableCellRenderer method.
     *
     * @param table
     * @param value
     * @param isSelected
     * @param hasFocus
     * @param row
     * @param column
     * @return
     */
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus, int row,
                                                   int column) {
        // Configure component.
        setText(value.toString());
        setBorder(BorderFactory.createEtchedBorder());
        setHorizontalAlignment(CENTER);

        // Set icon depending on the sort column / direction
        if (column == sortedTableModel.getSortColumn()) {
            if (sortedTableModel.isSortAscending()) {
                setIcon(Icons.SORT_UP);
            } else {
                setIcon(Icons.SORT_DOWN);
            }
        } else {

            // Set to blank icon to stop the text alignment jumping.
            setIcon(Icons.SORT_BLANK);
        }

        // Since the renderer is a component, return itself
        return this;
    }

    public void validate() {
    }

    public void revalidate() {
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    }

    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    }
}
