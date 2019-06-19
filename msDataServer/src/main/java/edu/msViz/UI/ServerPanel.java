package edu.msViz.UI;

import edu.msViz.msHttpApi.MsDataServer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

class ServerPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(ServerPanel.class.getName());

    private static final String START_TEXT = "Start Server";
    private static final String STOP_TEXT = "Stop Server";
    private static final String LAUNCH_TEXT = "Open Viewer";
    private static final String PORT_NUMBER = "3807";

    private MsDataServer dataServer;

    // UI items
    private JTextField portEntry;
    private JButton startStopButton;
    private JButton launchButton;

    // flag to track if server is running
    private boolean running = false;

    public ServerPanel(MsDataServer controlledServer) {
        this.dataServer = controlledServer;

        this.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);

        // ROW 0: port field
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel portLabel = new JLabel("Port:");
        portLabel.setHorizontalAlignment(JLabel.RIGHT);
        this.add(portLabel, c);

        portEntry = new JTextField(PORT_NUMBER);
        c.gridx = 1;
        this.add(portEntry, c);

        // ROW 1: start and browse buttons
        c.gridy = 1;

        startStopButton = new JButton(START_TEXT);
        startStopButton.setMnemonic('S');
        startStopButton.addActionListener(this::startStopClicked);
        c.gridx = 0;
        this.add(startStopButton, c);

        launchButton = new JButton(LAUNCH_TEXT);
        launchButton.setMnemonic('V');
        launchButton.setEnabled(false);
        launchButton.addActionListener(this::launchClicked);
        c.gridx = 1;
        this.add(launchButton, c);
    }

    private void openWebPage(String url) {
        URI uri = java.net.URI.create(url);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                Runtime.getRuntime().exec("xdg-open " + uri.toString());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not open web browser", e);
        }
    }

    private void startStopClicked(ActionEvent e) {
        if (!running) {
            try {
                int port = Integer.parseInt(portEntry.getText());
                this.dataServer.startServer(port);
                if (this.dataServer.waitUntilStarted()) {
                    launchClicked(null);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to start server.\nMake sure another server is not already running.");
                }
            } catch (NumberFormatException ex) {
                portEntry.setText(PORT_NUMBER);
            }
        } else {
            this.dataServer.stopServer();
        }
        running = !running;
        startStopButton.setText(running ? STOP_TEXT : START_TEXT);
        launchButton.setEnabled(running);
    }

    private void launchClicked(ActionEvent e) {
        URL dest;
        try {
            dest = new URL("http", "localhost", this.dataServer.getPort(), "");
            openWebPage(dest.toString());
        } catch (MalformedURLException ex) {
            JOptionPane.showMessageDialog(this, "Could not create URL to server.");
        }
    }

}
