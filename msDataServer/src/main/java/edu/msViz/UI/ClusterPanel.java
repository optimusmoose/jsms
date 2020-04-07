package edu.msViz.UI;

import edu.msViz.msHttpApi.MzTreePointDatabaseConnection;
import edu.msViz.mzTree.MzTree;
import edu.umt.ms.traceSeg.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

class ClusterPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(ClusterPanel.class.getName());

    private final StartFrame frame;

    private final JComboBox<String> models;
    private final JButton doTraceButton;
    private final JButton startButton;
    private final ButtonGroup frequentistOptions;
    private final JPanel frequentistOptionsPanel;
    private final JRadioButton cluster;
    private final JRadioButton train;
    private final JCheckBox useDefault;

    // model descriptions
    private static final String BAYESIAN_DESCRIPTION = "Uses prior knowledge to infer isotopic envelopes.";
    private static final String FREQUENTIST_DESCRIPTION = "Uses measured probabilites to infer isotopic envelopes.";
    private static final String HYBRID_DESCRIPTION = "Uses prior knowlege and measured probabilities to infer isotopic envelopes.";

    private static final String DEFAULT_PROBS_TOOLTIP =
            "Uses default measured probabilites, measured from UPS2 MS dataset.";

    private static final String CLUSTER_TOOLTIP =
            "Performs trace clustering on all traces in current dataset.";

    private static final String TRAIN_TOOLTIP =
            "Performs probability training on current dataset. Results can be stored in a new file or added to existing probability file.";

    public ClusterPanel(StartFrame frame) {
        this.frame = frame;

        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;

        // ROW 0: model
        c.gridy = 0;

        // model label
        c.gridx = 0;
        JLabel modelLabel = new JLabel("Model:");
        modelLabel.setHorizontalAlignment(JLabel.RIGHT);
        this.add(modelLabel, c);

        // model combo box
        models = new JComboBox<>(new String[]{"Bayesian", "Frequentist", "Hybrid"});
        models.addActionListener(this::modelChanged);
        models.setToolTipText(BAYESIAN_DESCRIPTION);
        c.gridx = 1;
        this.add(models, c);

        // ************** frequentist options ****************
        {
            frequentistOptionsPanel = new JPanel();
            frequentistOptionsPanel.setLayout(new GridBagLayout());
            GridBagConstraints fc = new GridBagConstraints();

            // frequentist radio buttons
            cluster = new JRadioButton("Cluster");
            cluster.setHorizontalAlignment(SwingConstants.CENTER);
            cluster.setSelected(true);
            cluster.addActionListener(this::clusterTrainSwitch);
            cluster.setToolTipText(CLUSTER_TOOLTIP);
            train = new JRadioButton("Train");
            train.setHorizontalAlignment(SwingConstants.CENTER);
            train.addActionListener(this::clusterTrainSwitch);
            train.setToolTipText(TRAIN_TOOLTIP);

            // radio button group
            frequentistOptions = new ButtonGroup();
            frequentistOptions.add(cluster);
            frequentistOptions.add(train);

            // freq. ROW 0: cluster and train radio buttons
            fc.gridy = 0;

            fc.gridx = 0;
            frequentistOptionsPanel.add(cluster, fc);

            // place train radio button
            fc.gridx = 1;
            frequentistOptionsPanel.add(train, fc);

            // freq. ROW 1: use default button
            fc.gridy = 1;

            useDefault = new JCheckBox("Default Probabilities");
            useDefault.setSelected(true);
            useDefault.setToolTipText(DEFAULT_PROBS_TOOLTIP);
            fc.gridwidth = 2;
            fc.gridx = 0;
            frequentistOptionsPanel.add(useDefault, fc);
        }

        // ROW 1: frequentist options
        c.gridy = 1;

        frequentistOptionsPanel.setVisible(false);
        c.gridx = 0;
        c.gridwidth = 2;
        this.add(frequentistOptionsPanel, c);

        // ROW 2: Start buttons
        c.fill = GridBagConstraints.NONE;
        c.gridy = 2;

        doTraceButton = new JButton("Trace");
        doTraceButton.addActionListener(this::doTraceClicked);
        c.gridx = 0;
        c.gridwidth = 1;
        this.add(doTraceButton, c);

        startButton = new JButton("Start");
        startButton.setMnemonic('T');
        startButton.addActionListener(this::startClicked);
        c.gridx = 1;
        this.add(startButton, c);

    }

    private void startClicked(ActionEvent e) {
        String model = (String) models.getSelectedItem();
        final boolean isTrain = train.isSelected();
        boolean isUseDefault = useDefault.isSelected();
        final String filePath;

        // user choose file if not using bayesian model...
        if (!model.equalsIgnoreCase("Bayesian") && (isTrain || !isUseDefault)) {
            // ...and chose to train or chose not to use default probabilities
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Probability Files", "json"));
            fileChooser.setDialogTitle(isTrain ? "Create or Select Probability File" : "Select Probability File");
            int fileChooserResult = fileChooser.showOpenDialog(this.frame);
            if (fileChooserResult == JFileChooser.APPROVE_OPTION)
                filePath = fileChooser.getSelectedFile().getPath();
            else
                return;
        } else
            filePath = null;


        this.startButton.setEnabled(false);
        this.startButton.setText("Working...");

        // begin processing cluster request on background thread
        new Thread() {
            @Override
            public void run() {
                LOGGER.log(Level.INFO, "Beginning trace clustering");
                try {
                    LOGGER.log(Level.INFO, frame.mzTree.executeClusteringCommand(model, isTrain, filePath));
                    JOptionPane.showMessageDialog(frame, "Finished trace clustering.", "Clustering Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error during clustering", ex);
                    JOptionPane.showMessageDialog(frame, "Error during trace clustering: " + ex.getMessage(), "Clustering Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    startButton.setEnabled(true);
                    startButton.setText("Start");
                }
            }
        }.start();
    }

    private void clusterTrainSwitch(ActionEvent e) {
        if (cluster.isSelected())
            useDefault.setEnabled(true);
        else
            useDefault.setEnabled(false);
    }

    private void modelChanged(ActionEvent e) {
        String action = (String) models.getSelectedItem();
        switch (action) {
            case "Bayesian":
                this.models.setToolTipText(BAYESIAN_DESCRIPTION);
                frequentistOptionsPanel.setVisible(false);
                break;
            case "Frequentist":
                this.models.setToolTipText(FREQUENTIST_DESCRIPTION);
                frequentistOptionsPanel.setVisible(true);
                break;
            case "Hybrid":
                this.models.setToolTipText(HYBRID_DESCRIPTION);
                frequentistOptionsPanel.setVisible(true);
                break;
        }
    }

    private Thread traceThread;
    private void doTraceClicked(ActionEvent e) {
        if (traceThread == null) {
            MzTree mzTree = frame.mzTree;
            SwingUtilities.invokeLater(() -> doTraceButton.setText("Stop Trace"));
            traceThread = new Thread(() -> {
                MzTreePointDatabaseConnection connection = new MzTreePointDatabaseConnection(mzTree);
                TraceSegmenter segmenter = new TraceSegmenter(connection);
                try {
                    LOGGER.log(Level.INFO, "Starting trace segmentation");
                    segmenter.run();
                    LOGGER.log(Level.INFO, "Trace segmentation complete");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failure in trace segmentation", ex);
                }
                SwingUtilities.invokeLater(() -> doTraceButton.setText("Trace"));
                traceThread = null;
            });
            traceThread.start();
        } else {
            traceThread.interrupt();
            try {
                traceThread.join();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Interrupted while waiting to join trace thread", ex);
            }
            SwingUtilities.invokeLater(() -> doTraceButton.setText("Trace"));
        }
    }
}
