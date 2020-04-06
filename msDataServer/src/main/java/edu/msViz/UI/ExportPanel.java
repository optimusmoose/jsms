package edu.msViz.UI;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import edu.msViz.mzTree.IO.CsvExporter;
import edu.msViz.mzTree.IO.LabelledMsDataRange;
import edu.msViz.mzTree.IO.MsDataRange;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/**
 * Panel with controls to export data ranges to csv
 * @author kyle
 */
class ExportPanel extends JPanel {

    /**
     * Logger
     */
    private static final Logger LOGGER = Logger.getLogger(ExportPanel.class.getName());

    /**
     * Parent frame
     */
    private final StartFrame frame;

    /**
     * Selected: output segmented data within ranges only
     * False: output all data within ranges
     */
    private JCheckBox segmentedOnly;

    /**
     * Embedded range table
     */
    private RangeTable rangeTable;

    /**
     * Default constructor. Constructs and adds sub-panels
     * @param frame parent frame
     */
    public ExportPanel(StartFrame frame) {
        this.frame = frame;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.add(this.constructExportControlPanel());
        this.add(this.constructTablePanel());
        this.add(this.constructRangeControlPanel());

    }

    /**
     * Constructs a panel with controls for exporting data
     * @return constructed panel
     */
    private JPanel constructExportControlPanel()
    {
        JPanel exportControlPanel = new JPanel();
        exportControlPanel.setLayout(new BoxLayout(exportControlPanel,BoxLayout.X_AXIS));
        // // export button
        // JButton exportButton = new JButton("Export...");
        // exportButton.setMnemonic('E');
        // exportButton.addActionListener(e -> this.exportClicked(e));
        // exportButton.setToolTipText("Export data ranges from MzTree to CSV");
        // exportControlPanel.add(exportButton);
        // exportControlPanel.add(Box.createRigidArea(new Dimension(5,0)));
        //
        // export all button
        JButton exportAllButton = new JButton("Points");
        exportAllButton.setMnemonic('P');
        exportAllButton.addActionListener(e -> this.exportAllClicked(e));
        exportAllButton.setToolTipText("Export all MzTree points to CSV (ignore ranges)");
        exportControlPanel.add(exportAllButton);
        exportControlPanel.add(Box.createRigidArea(new Dimension(5,0)));

        // segmented only checkbox
        this.segmentedOnly = new JCheckBox("Segmented Only");
        this.segmentedOnly.setMnemonic('G');
        this.segmentedOnly.setToolTipText("Export only data that have been segmented");
        exportControlPanel.add(this.segmentedOnly);
        exportControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        // segmented only checkbox
        JButton exportTraceButton = new JButton("Traces");
        exportTraceButton.setMnemonic('T');
        exportTraceButton.addActionListener(e -> this.exportTraceClicked(e));
        exportTraceButton.setToolTipText("Export all Traces with stats to CSV");
        exportControlPanel.add(exportTraceButton);
        exportControlPanel.add(Box.createRigidArea(new Dimension(5,0)));

        // // segmented only checkbox
        // JButton exportEnvelopeButton = new JButton("Envelopes");
        // exportEnvelopeButton.setMnemonic('E');
        // exportEnvelopeButton.addActionListener(e -> this.exportEnvelopeClicked(e));
        // exportEnvelopeButton.setToolTipText("Export all Envelopes with stats to CSV");
        // exportControlPanel.add(exportEnvelopeButton);
        // exportControlPanel.add(Box.createRigidArea(new Dimension(5,0)));

        return exportControlPanel;

    }

    /**
     * Constructs a scroll pane containing the data range table
     * @return scroll pane with data range panel
     */
    private JScrollPane constructTablePanel()
    {
        this.rangeTable = new RangeTable();
        JScrollPane tablePanel = new JScrollPane(this.rangeTable);
        tablePanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        return tablePanel;
    }

    /**
     * Constructs a panel with controls for managing data ranges in table
     * @return constructed panel
     */
    private JPanel constructRangeControlPanel()
    {
        JPanel rangeControlPanel = new JPanel();
        rangeControlPanel.setLayout(new BoxLayout(rangeControlPanel,BoxLayout.X_AXIS));

        // load button
        JButton loadButton = new JButton("Load...");
        loadButton.setMnemonic('D');
        loadButton.addActionListener(this::loadClicked);
        loadButton.setToolTipText("Load data ranges file");
        rangeControlPanel.add(loadButton);
        rangeControlPanel.add(Box.createRigidArea(new Dimension(5,0)));

        // delete row button
        JButton deleteRowButton = new JButton("Delete Row");
        deleteRowButton.setMnemonic('T');
        deleteRowButton.addActionListener(this::deleteRowClicked);
        deleteRowButton.setToolTipText("Delete the selected row");
        rangeControlPanel.add(deleteRowButton);
        rangeControlPanel.add(Box.createRigidArea(new Dimension(5,0)));

        // clear button
        JButton clearButton = new JButton("Clear");
        clearButton.setMnemonic('L');
        clearButton.addActionListener(this::clearClicked);
        clearButton.setToolTipText("Delete all rows");
        rangeControlPanel.add(clearButton);

        rangeControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return rangeControlPanel;
    }

    /**
     * Event (click) listener for load button. Prompts the user with a file chooser,
     * loads contained data
     * @param e
     */
    private void loadClicked(ActionEvent e)
    {
        // display file chooser (tsv data ranges file)
        JFileChooser rangeFileChooser = new JFileChooser();
        rangeFileChooser.setDialogTitle("Load Ranges File");
        rangeFileChooser.setFileFilter(new FileNameExtensionFilter(".tsv", "tsv"));
        int outputFileResult = rangeFileChooser.showSaveDialog(this.frame);

        // continue only if file selected
        if (outputFileResult == JFileChooser.APPROVE_OPTION)
        {
            // selected file
            String filepath = rangeFileChooser.getSelectedFile().getPath();

            // read in data
            try(CSVReader reader = new CSVReaderBuilder(new FileReader(filepath)).withCSVParser(new CSVParserBuilder().withSeparator('\t').build()).build())
            {
                // iterate through lines in file
                List<String[]> lines = reader.readAll();
                for(int i = 0; i < lines.size(); i++)
                {
                    // attempt to parse row
                    try{

                        // parse values
                        String[] line = lines.get(i);
                        double mzMin = Double.valueOf(line[0]);
                        double mzMax = Double.valueOf(line[1]);
                        float rtMin = Float.valueOf(line[2]);
                        float rtMax = Float.valueOf(line[3]);

                        // create range (labelled range if label provided)
                        MsDataRange range;
                        if(line.length == 5)
                            range = new LabelledMsDataRange(line[4],mzMin,mzMax,rtMin,rtMax);
                        else
                            range = new MsDataRange(mzMin,mzMax,rtMin,rtMax);

                        this.rangeTable.appendRow(range);
                    }

                    // log failed rows
                    catch(Exception ex)
                    {
                        LOGGER.log(Level.WARNING, "Failed to load data range {0}, {1}", new Object[]{String.valueOf(i+1), ex.getMessage()});
                    }
                }
            }

            catch(IOException ex)
            {
                LOGGER.log(Level.SEVERE, "Error when loading ranges file", ex);
                JOptionPane.showMessageDialog(this.frame, "Could not load ranges file: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Event (click) listener for export all button. Exports all data within
     * MzTree, subject to segmented only option
     * @param e
     */
    private void exportAllClicked(ActionEvent e)
    {
        // export entire MzTree data range, subject to segmented only option
        this.performExport(Arrays.asList(this.frame.mzTree.getDataBounds()),this.segmentedOnly.isSelected());
    }

    private void exportTraceClicked(ActionEvent e)
    {
      this.performTraceExport();

    }

    // private void exportEnvelopeClicked(ActionEvent e)
    // {
    //   this.performEnvelopeExport();
    //
    // }

    /**
     * Event (click) listener for export button. Exports all specified data ranges from MzTree file,
     * subject to segmented only option
     * @param e
     */
    private void exportClicked(ActionEvent e)
    {
        // get valid ranges from table
        List<MsDataRange> ranges = this.rangeTable.getValidDataRanges();

        // export specified ranges from MzTree, subject to segmented only option
        this.performExport(ranges, this.segmentedOnly.isSelected());
    }

    /**
     * Event (click) listener for clear button. Removes all data ranges (rows) from table
     * @param e
     */
    private void clearClicked(ActionEvent e)
    {
        this.rangeTable.removeAll();
        this.rangeTable.appendEmptyRow();
    }

    /**
     * Event (click) listener for delete row button. Deletes the row with focus
     * @param e
     */
    private void deleteRowClicked(ActionEvent e)
    {
        this.rangeTable.removeSelectedRow();
    }

    /**
     * Prompts the user for an export destination, exports the given ranges to the destination
     * @param exportRanges ranges to selected from mzTree
     * @param onlySegmented if true, only segmented data in ranges are exported. otherwise all data are exported
     */
    private void performTraceExport()
    {
      JFileChooser outputFileChooser = new JFileChooser();
      outputFileChooser.setDialogTitle("Export");
      outputFileChooser.setFileFilter(new FileNameExtensionFilter(".csv", "csv"));
      int outputFileResult = outputFileChooser.showSaveDialog(this.frame);
      if (outputFileResult == JFileChooser.APPROVE_OPTION)
      {
        String filepath = outputFileChooser.getSelectedFile().getPath();
        try (CsvExporter exporter = new CsvExporter(filepath))
        {
            // perform export
            int numExported = exporter.exportTraces(this.frame.mzTree);

            // report results
            LOGGER.log(Level.INFO, "Exported {0} traces to {1}", new Object[] {numExported, exporter.getDestinationPath()});
            JOptionPane.showMessageDialog(this.frame, "Finished CSV export to\n " + exporter.getDestinationPath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.SEVERE, "Error when exporting CSV file", ex);
            JOptionPane.showMessageDialog(this.frame, "Could not export to CSV file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }
    //
    // private void performEnvelopeExport()
    // {
    //   JFileChooser outputFileChooser = new JFileChooser();
    //   outputFileChooser.setDialogTitle("Export");
    //   outputFileChooser.setFileFilter(new FileNameExtensionFilter(".csv", "csv"));
    //   int outputFileResult = outputFileChooser.showSaveDialog(this.frame);
    //   if (outputFileResult == JFileChooser.APPROVE_OPTION)
    //   {
    //     String filepath = outputFileChooser.getSelectedFile().getPath();
    //     try (CsvExporter exporter = new CsvExporter(filepath))
    //     {
    //         // perform export
    //         int numExported = exporter.exportEnvelopes(this.frame.mzTree);
    //
    //         // report results
    //         LOGGER.log(Level.INFO, "Exported {0} data points to {1}", new Object[] {numExported, exporter.getDestinationPath()});
    //         JOptionPane.showMessageDialog(this.frame, "Finished CSV export to\n " + exporter.getDestinationPath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
    //     }
    //     catch (Exception ex)
    //     {
    //         LOGGER.log(Level.SEVERE, "Error when exporting CSV file", ex);
    //         JOptionPane.showMessageDialog(this.frame, "Could not export to CSV file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
    //     }
    //   }
    // }

    private void performExport(List<MsDataRange> exportRanges, boolean onlySegmented)
    {
        // display file chooser for output path selection
        JFileChooser outputFileChooser = new JFileChooser();
        outputFileChooser.setDialogTitle("Export");
        outputFileChooser.setFileFilter(new FileNameExtensionFilter(".csv", "csv"));
        int outputFileResult = outputFileChooser.showSaveDialog(this.frame);

        // continue only if file selected
        if (outputFileResult == JFileChooser.APPROVE_OPTION)
        {
            // selected filepath
            String filepath = outputFileChooser.getSelectedFile().getPath();

            // output all data ranges to selected filepath, subject to segmented only filepath
            try (CsvExporter exporter = new CsvExporter(filepath))
            {
                // perform export
                int numExported = exporter.export(exportRanges, this.frame.mzTree, onlySegmented);

                // report results
                LOGGER.log(Level.INFO, "Exported {0} data points to {1}", new Object[] {numExported, exporter.getDestinationPath()});
                JOptionPane.showMessageDialog(this.frame, "Finished CSV export to\n " + exporter.getDestinationPath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.SEVERE, "Error when exporting CSV file", ex);
                JOptionPane.showMessageDialog(this.frame, "Could not export to CSV file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * JTable extended to contain LabelledMsDataRange objects
     */
    private class RangeTable extends JTable
    {
        // headers
        private final String labelHeader = "Label";
        private final String minMzHeader = "Min m/z"; private final String maxMzHeader = "Max m/z";
        private final String minRtHeader = "Min RT"; private final String maxRtHeader = "Max RT";

        // column ordering and corresponding types
        private final List<String> columnHeaders = Arrays.asList(new String[] { labelHeader, minMzHeader, maxMzHeader, minRtHeader, maxRtHeader });
        private final List<Class<?>> columnTypes = Arrays.asList(new Class<?>[] { String.class, Double.class, Double.class, Double.class, Double.class });

        // columns required for a row to be considered complete
        private final HashSet<String> requiredColumns = new HashSet<>(Arrays.asList(new String[] { minMzHeader, maxMzHeader, minRtHeader, maxRtHeader }));

        /**
         * Default constructor
         */
        public RangeTable()
        {
            super();

            // add columns to table
            DefaultTableModel model = (DefaultTableModel)this.getModel();
            for(String columnHeader : columnHeaders)
                model.addColumn(columnHeader);

            // assign renderer
            ColorfulTableCellRenderer cellRenderer = new ColorfulTableCellRenderer();
            this.setDefaultRenderer(Object.class, cellRenderer);

            // create empty row for entering new text
            this.appendEmptyRow();

            // enable programatic selection of a single cell
            this.setCellSelectionEnabled(true);

            // add table cell edit listener to table's model listeners
            model.addTableModelListener(tableChangedListener);

        }

        /*
        private void initKeyBindings()
        {
            JTable table = this;
            this.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e)
                {
                    if(e.getID() == KeyEvent.KEY_TYPED && e.getKeyCode() == KeyEvent.VK_ENTER)
                    {
                        int selectedRowIX = table.getSelectionModel().getMinSelectionIndex();
                        int selectedColIX = table.getColumnModel().getSelectionModel().getMinSelectionIndex();


                    }
                }

                @Override
                public void keyPressed(KeyEvent e) {}


                @Override
                public void keyReleased(KeyEvent e) {}


            });
        }*/

        /**
         * Override
         * Selects all text on cell edit
         * @param rowIX row index of edited cell
         * @param colIX column index of edited cell
         * @param e event type
         * @return Result of super call
         */
        @Override
        public boolean editCellAt(int rowIX, int colIX, EventObject e)
        {
            boolean superResult = super.editCellAt(rowIX, colIX, e);

            Component editor = this.getEditorComponent();

            if(editor == null)
                return superResult;

            if(editor instanceof JTextComponent)
            {
                if(e instanceof KeyEvent)
                    ((JTextComponent) editor).selectAll();
                if(e instanceof MouseEvent)
                    EventQueue.invokeLater(() -> {((JTextComponent) editor).selectAll();});
            }

            return superResult;
        }

        /**
         * Override
         * Removes all rows in table and colorful cell renderer
         */
        @Override
        public void removeAll()
        {
            // remove all rows
            super.removeAll();
            ((DefaultTableModel)this.getModel()).setRowCount(0);

            // remove all row colors
            ColorfulTableCellRenderer cellRenderer = (ColorfulTableCellRenderer)this.getCellRenderer(0, 0);
            cellRenderer.removeAllRows();
        }

        /**
         * Removes the row of the currently selected cell.
         * Prevents removing the empty, last row
         */
        public void removeSelectedRow()
        {
            // current selected cell indices
            int rowIX = this.getSelectedRow();
            int colIX = this.getSelectedColumn();

            // empty row cannot be removed.
            if(rowIX != this.getRowCount() - 1)
                this.removeRow(rowIX);

            // select the same rowIX (now the next row) to support repetitive removing
            this.changeSelection(rowIX, colIX, false, false);

            this.requestFocus();
        }

        /**
         * Remove the row at the given index
         * @param rowIX index of row to remove
         */
        private void removeRow(int rowIX)
        {
            // remove the row
            DefaultTableModel model = (DefaultTableModel)this.getModel();
            model.removeRow(rowIX);

            // remove the row's color
            ColorfulTableCellRenderer cellRenderer = (ColorfulTableCellRenderer)this.getCellRenderer(rowIX, 0);
            cellRenderer.rowDeleted(rowIX);
        }

        /**
         * Appends a new range to the table, replacing the empty row at the final position
         * @param range range to append
         */
        private void appendRow(MsDataRange range)
        {
            DefaultTableModel model = (DefaultTableModel)this.getModel();

            // remove empty row at end of model
            this.removeRow(model.getRowCount() - 1);

            // add range to table. if labelled, include label
            String label = range instanceof LabelledMsDataRange ? ((LabelledMsDataRange)range).label : null;
            isUserEdit = false;
            model.addRow(new Object[] {label, range.mzMin, range.mzMax, (double)range.rtMin, (double)range.rtMax});

            // add color to renderer. white for valid range, red-ish for invalid range
            Color rowColor = this.isValidRangeRow(model.getRowCount()-1) ? Color.WHITE : Color.decode(ColorfulTableCellRenderer.INVALID_COLOR_HEX);
            ((ColorfulTableCellRenderer)this.getDefaultRenderer(Object.class)).newRow(rowColor);

            // append an empty row for additional manual entries
            this.appendEmptyRow();
        }

        /**
         * Updates the color for the specified row according to the row's validity
         * @param rowIX index of row to recolor
         */
        private void updateRowColor(int rowIX)
        {
            DefaultTableModel model = (DefaultTableModel)this.getModel();
            if(this.isValidRangeRow(rowIX))
                ((ColorfulTableCellRenderer)this.getCellRenderer(rowIX, 0)).validRow(rowIX);
            else
                ((ColorfulTableCellRenderer)this.getCellRenderer(rowIX, 0)).invalidRow(rowIX);
            isUserEdit = false;
            model.fireTableRowsUpdated(rowIX, rowIX);
        }

        /**
         * Checks if the specified row has values for each required column
         * @param rowIX index of row for which to check completion
         * @return true if row is complete, else false
         */
        private boolean isCompleteRow(int rowIX)
        {
            DefaultTableModel model = (DefaultTableModel)this.getModel();

            // iterate through each column
            for(int i = 0; i < model.getColumnCount(); i ++)
            {
                // if column is required...
                if(requiredColumns.contains(model.getColumnName(i)))
                {
                    // ... and value is empty
                    Object colVal = model.getValueAt(rowIX, i);
                    if(colVal == null)
                        // the row is not complete
                        return false;
                }
            }

            // if loop succeeds then row is complete
            return true;
        }

        /**
         * Appends an empty row to the table
         */
        public final void appendEmptyRow()
        {
            DefaultTableModel model = (DefaultTableModel)this.getModel();

            // add new, empty row to table
            model.addRow(new Object[] {null,null,null,null,null});

            // add new color row to colorful renderer
            ((ColorfulTableCellRenderer)this.getDefaultRenderer(Object.class)).newRow();
        }

        /**
         * Checks if a specified row has a valid data range.
         * A valid data range is one where each complete dimension's min <= max.
         * Incomplete (partially or completely null) dimensions are considered valid.
         * @param rowIX index of row to check
         * @return true if valid, false otherwise
         */
        private boolean isValidRangeRow(int rowIX)
        {
            DefaultTableModel model = (DefaultTableModel)this.getModel();

            // valid if mz min <= mz max and rtmin <= rtmax
            Double minMz = (Double)model.getValueAt(rowIX, this.columnHeaders.indexOf(minMzHeader));
            Double maxMz = (Double)model.getValueAt(rowIX, this.columnHeaders.indexOf(maxMzHeader));
            Double minRt = (Double)model.getValueAt(rowIX, this.columnHeaders.indexOf(minRtHeader));
            Double maxRt = (Double)model.getValueAt(rowIX, this.columnHeaders.indexOf(maxRtHeader));

            // if both mz values are not empty
            if(minMz != null && maxMz != null)
            {
                // ensure min mz <= max mz
                if(minMz > maxMz)
                    return false;
            }

            // if both RT values are not empty
            if(minRt != null && maxRt != null)
            {
                // ensure min mz <= max mz
                if(minRt > maxRt)
                    return false;
            }

            // made it this far, both dimension ranges are valid
            return true;
        }

        /**
         * Checks if the given string value is valid for the specified column's datatype
         * @param value value to validate
         * @param colIX index of targeted column
         * @return true if value is valid for column at colIX
         */
        private boolean isValidColumnValue(String value, int colIX)
        {
            // all cells are allowed to be empty
            if(value.equals(""))
                return true;

            // column type
            Class<?> columnType = columnTypes.get(colIX);

            if (columnType == String.class) {
                return true;
            } else if (columnType == Double.class) {
                try {
                    Double.valueOf(value);
                    return true;
                } catch (NumberFormatException ex) {
                    return false;
                }
            } else {
                throw new UnsupportedOperationException("Column type has no validation code: " + columnType.getSimpleName());
            }
        }

        /**
         * Converts JTextComponent input strings to the datatype required for a column
         * @param val input string value from table's cell (JTextComponent)
         * @param colIX index of column for which to convert the value
         * @return converted value
         */
        private Object produceColumnValue(String val, int colIX)
        {
            // Number types: int, double, float -> Double
            if(Number.class.isAssignableFrom(this.columnTypes.get(colIX)))
                return Double.valueOf(val);

            // else string type return provided value
            else
                return val;

        }

        /**
         * Collects the valid data ranges from the table
         * @return List of valid data ranges
         */
        public List<MsDataRange> getValidDataRanges()
        {
            List<MsDataRange> validRanges = new ArrayList<>();
            DefaultTableModel model = (DefaultTableModel)this.getModel();

            // iterate through each row
            for(int i = 0; i < model.getRowCount(); i++)
            {
                // if the row is complete and valid
                if(this.isValidRangeRow(i) && this.isCompleteRow(i))
                {
                    // collect the row's data range
                    Double minMz = (Double)model.getValueAt(i, this.columnHeaders.indexOf(minMzHeader));
                    Double maxMz = (Double)model.getValueAt(i, this.columnHeaders.indexOf(maxMzHeader));
                    Double minRt = (Double)model.getValueAt(i, this.columnHeaders.indexOf(minRtHeader));
                    Double maxRt = (Double)model.getValueAt(i, this.columnHeaders.indexOf(maxRtHeader));
                    MsDataRange range = new MsDataRange(minMz, maxMz, minRt.floatValue(), maxRt.floatValue());
                    validRanges.add(range);
                }
            }
            return validRanges;
        }

        /**
         * True: call to tableChangedListener were prompted by a user action
         * False: call to tableChangedListener was prompted by program action
         */
        private boolean isUserEdit = true;
        /**
         * Event listener on table model changes. Converts input string value to correct type,
         * updates the modified row's color, appends and empty row if final row is complete
         */
        private final TableModelListener tableChangedListener = (TableModelEvent e)->
        {
            DefaultTableModel model = (DefaultTableModel)e.getSource();

            // get cell coordinates
            int row = e.getFirstRow();
            int col = e.getColumn();

            // col == -1 when entering a new row
            // don't process inserting new row
            if(isUserEdit && col != -1)
            {
                // get the new value
                String val = (String)model.getValueAt(row, col);

                // if invalid entry erase the value
                isUserEdit = false;
                if(!this.isValidColumnValue(val, col))
                    model.setValueAt(null, row, col);
                else
                    model.setValueAt(val.equals("") ? null : this.produceColumnValue(val, col), row, col);


                // if valid entry in final row and final row is complete, add new empty row
                if(row == model.getRowCount() - 1 && this.isCompleteRow(row))
                    this.appendEmptyRow();

                // update color of modified row according to row validity
                this.updateRowColor(row);

            }
            else
            {
                isUserEdit = true;
            }
        };

        /**
         * Cell renderer that tracks and renders row colors
         */
        private final class ColorfulTableCellRenderer extends DefaultTableCellRenderer
        {
            /**
             * color for row in corresponding table position
             */
            public List<Color> rowColors = new LinkedList<>();

            /**
             * RGB color string for invalid rows
             */
            public static final String INVALID_COLOR_HEX = "#ffb3b3";

            public final Color invalidColor = Color.decode(INVALID_COLOR_HEX);

            public final Color validColor = Color.WHITE;

            /**
             * Sets the color for the row at rowIX to the invalid row color
             * @param rowIX row index
             */
            public void invalidRow(int rowIX)
            {
                rowColors.set(rowIX, this.invalidColor);
            }

            /**
             * Sets the color for the row at rowIX to the valid row color (WHITE)
             * @param rowIX row index
             */
            public void validRow(int rowIX)
            {
                rowColors.set(rowIX, this.validColor);
            }

            /**
             * Adds a valid color for a newly created row
             */
            public void newRow()
            {
                rowColors.add(this.validColor);
            }

            /**
             * Deletes the color for a deleted table row
             * @param rowIX index of deleted row
             */
            public void rowDeleted(int rowIX)
            {
                rowColors.remove(rowIX);
            }

            /**
             * Appends a new color
             * @param c color for corresponding new row
             */
            public void newRow(Color c)
            {
                rowColors.add(c);
            }

            /**
             * Clears the color list
             */
            public void removeAllRows()
            {
                rowColors = new ArrayList<>();
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColors.get(row));
                return c;
            }
        }

    }
}
