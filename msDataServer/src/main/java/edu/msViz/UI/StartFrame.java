package edu.msViz.UI;

import edu.msViz.UI.FilePanel;
import edu.msViz.UI.ExportPanel;
import edu.msViz.UI.ClusterPanel;
import edu.msViz.msHttpApi.MsDataServer;
import javax.swing.*;
import javax.swing.border.TitledBorder;

import edu.msViz.mzTree.MzTree;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.logging.*;

/**
 * MsDataServer GUI class
 * Swing implemented
 * 
 */
public class StartFrame extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(StartFrame.class.getName());

    // MsDataServer instance
    public MsDataServer dataServer = new MsDataServer();

    // currently loaded MzTree instance
    public MzTree mzTree;
    
    /**
     * Program start. Configures and displays StartFrame 
     * @param args command line args
     */
    public static void main(String[] args) {
        StartFrame frame = new StartFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JTextField serverStatusText;
    private FilePanel filePanel;
    private JTabbedPane tabPane;

    /**
     * Default constructor
     * @param frameTitle title of JFrame
     */
    private StartFrame(){
        super("msViz");

        JPanel startPanel = new JPanel();
        startPanel.setLayout(new BorderLayout());

        // create an error text area
        JTextArea errorDisplay = new JTextArea();
        errorDisplay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        errorDisplay.setEditable(false);

        // make the error text scrollable
        JScrollPane errorScrollPane = new JScrollPane(errorDisplay);
        errorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // add to the panel and register for error logging
        startPanel.add(errorScrollPane, BorderLayout.CENTER);
        LogManager.getLogManager().getLogger("").addHandler(new TextAreaLogHandler(errorDisplay));
        LOGGER.log(Level.INFO, "Error logging ready.");

        // add the status line
        serverStatusText = new JTextField("Status: Unknown.");
        serverStatusText.setEditable(false);
        startPanel.add(serverStatusText, BorderLayout.PAGE_END);

        // add main content area and file buttons
        JPanel mainPanel = new JPanel();
        mainPanel.setPreferredSize(new Dimension(550, 350));
        mainPanel.setLayout(new BorderLayout());

        filePanel = new FilePanel(this);
        filePanel.setBorder(new TitledBorder("File"));
        mainPanel.add(filePanel, BorderLayout.PAGE_START);

        // add the operations tabs
        tabPane = new JTabbedPane();
        tabPane.addTab("Server", new ServerPanel(this.dataServer));
        tabPane.setMnemonicAt(0, KeyEvent.VK_R);
        tabPane.addTab("Export", new ExportPanel(this));
        tabPane.setMnemonicAt(1, KeyEvent.VK_X);
        tabPane.addTab("Cluster", new ClusterPanel(this));
        tabPane.setMnemonicAt(2, KeyEvent.VK_L);
        mainPanel.add(tabPane, BorderLayout.CENTER);

        setFileOpenState(false);

        startPanel.add(mainPanel, BorderLayout.LINE_START);

        this.setContentPane(startPanel);
        this.pack();
    }

    public void setStatusText(String status) {
        this.serverStatusText.setText(status);
    }

    public void setFileOpenState(boolean state) {
        filePanel.setFileOpenState(state);
        tabPane.setEnabledAt(tabPane.indexOfTab("Export"), state);
        tabPane.setEnabledAt(tabPane.indexOfTab("Cluster"), state);
        if (!state) {
            tabPane.setSelectedIndex(tabPane.indexOfTab("Server"));
        }
    }


    private static class TextAreaLogHandler extends Handler {
        private JTextArea textArea;
        public TextAreaLogHandler(JTextArea textArea) {
            this.textArea = textArea;

            setLevel(Level.INFO);
            setFormatter(new SimpleFormatter());
        }

        @Override
        public void publish(LogRecord record) {
            textArea.append(getFormatter().format(record));
        }

        @Override
        public void flush() {}
        @Override
        public void close() throws SecurityException {}
    }
}
