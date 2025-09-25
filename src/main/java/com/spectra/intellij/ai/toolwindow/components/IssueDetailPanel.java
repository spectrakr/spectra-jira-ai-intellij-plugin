package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.openapi.project.Project;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.toolwindow.handlers.InlineEditHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Consumer;

public class IssueDetailPanel extends JPanel {
    
    private final Project project;
    private JTextField issueKeyField;
    private JButton hamburgerMenuButton;
    private JTextField issueSummaryField;
    private JComboBox<String> issueStatusComboBox;
    private JTextArea issueDescriptionField;
    private JScrollPane descriptionScrollPane;
    private JLabel assigneeLabel;
    private JLabel reporterLabel;
    private JTextField storyPointsField;
    private JLabel epicLabel;
    private JPanel descriptionButtonPanel;
    private JButton saveDescriptionButton;
    private JButton cancelDescriptionButton;
    
    private Consumer<Void> onHamburgerMenuClick;
    private Consumer<Void> onAssigneeLabelClick;
    private Consumer<Void> onEpicLabelClick;
    
    public IssueDetailPanel(Project project) {
        this.project = project;
        initializeComponents();
        setupResizeListener();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        
        // Main form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Issue Key with hamburger menu
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel issueKeyPanel = new JPanel(new BorderLayout());
        
        issueKeyField = new JTextField();
        issueKeyField.setEditable(false);
        issueKeyField.setFont(issueKeyField.getFont().deriveFont(Font.BOLD, 14f));
        issueKeyField.setBackground(formPanel.getBackground());
        issueKeyField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        issueKeyPanel.add(issueKeyField, BorderLayout.CENTER);
        
        // Hamburger menu button
        hamburgerMenuButton = new JButton("☰");
        hamburgerMenuButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        hamburgerMenuButton.setPreferredSize(new Dimension(30, 30));
        hamburgerMenuButton.setMargin(new Insets(2, 2, 2, 2));
        hamburgerMenuButton.setBorderPainted(false);
        hamburgerMenuButton.setFocusPainted(false);
        hamburgerMenuButton.setContentAreaFilled(false);
        hamburgerMenuButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hamburgerMenuButton.addActionListener(e -> {
            if (onHamburgerMenuClick != null) {
                onHamburgerMenuClick.accept(null);
            }
        });
        hamburgerMenuButton.setVisible(false);
        issueKeyPanel.add(hamburgerMenuButton, BorderLayout.EAST);
        
        formPanel.add(issueKeyPanel, gbc);
        
        // Summary (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueSummaryField = new JTextField();
        issueSummaryField.setFont(issueSummaryField.getFont().deriveFont(Font.BOLD, 12f));
        // Initial size will be set by GridBagConstraints HORIZONTAL fill
        formPanel.add(issueSummaryField, gbc);
        
        // Status (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST;
        issueStatusComboBox = new JComboBox<>();
        issueStatusComboBox.setPreferredSize(new Dimension(200, issueStatusComboBox.getPreferredSize().height));
        formPanel.add(issueStatusComboBox, gbc);
        
        // Description (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.weighty = 0; gbc.gridwidth = 2;
        
        // Create description panel container
        JPanel descriptionPanel = new JPanel(new BorderLayout());
        
        issueDescriptionField = new JTextArea();
        issueDescriptionField.setLineWrap(true);
        issueDescriptionField.setWrapStyleWord(true);
        issueDescriptionField.setRows(3); // Reduce rows to prevent overflow
        descriptionScrollPane = new JScrollPane(issueDescriptionField);
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionScrollPane.setPreferredSize(new Dimension(350, 90)); // Set initial size with reduced height\n        descriptionScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90)); // Limit maximum height
        descriptionPanel.add(descriptionScrollPane, BorderLayout.CENTER);
        
        // Create button panel for description editing (initially hidden)
        descriptionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        saveDescriptionButton = new JButton("저장");
        cancelDescriptionButton = new JButton("취소");
        
        descriptionButtonPanel.add(saveDescriptionButton);
        descriptionButtonPanel.add(cancelDescriptionButton);
        descriptionButtonPanel.setVisible(false); // Initially hidden
        
        descriptionPanel.add(descriptionButtonPanel, BorderLayout.SOUTH);
        formPanel.add(descriptionPanel, gbc);
        
        // 세부사항 영역
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.weighty = 0; gbc.gridwidth = 2;
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder("세부사항"));
        
        GridBagConstraints detailGbc = new GridBagConstraints();
        detailGbc.insets = new Insets(3, 5, 3, 5);
        detailGbc.anchor = GridBagConstraints.WEST;
        
        // 담당자
        detailGbc.gridx = 0; detailGbc.gridy = 0;
        detailsPanel.add(new JLabel("담당자:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        
        // Create assignee label with click functionality
        assigneeLabel = new JLabel("할당되지 않음");
        assigneeLabel.setOpaque(true);
        assigneeLabel.setBackground(UIManager.getColor("Label.background"));
        assigneeLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        assigneeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailsPanel.add(assigneeLabel, detailGbc);
        
        // 보고자
        detailGbc.gridx = 0; detailGbc.gridy = 1; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0;
        detailsPanel.add(new JLabel("보고자:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        reporterLabel = new JLabel();
        detailsPanel.add(reporterLabel, detailGbc);
        
        // Epic (상위항목)
        detailGbc.gridx = 0; detailGbc.gridy = 2; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0;
        detailsPanel.add(new JLabel("상위항목:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        epicLabel = new JLabel("없음");
        epicLabel.setOpaque(true);
        epicLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        epicLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailsPanel.add(epicLabel, detailGbc);
        
        // Story Points
        detailGbc.gridx = 0; detailGbc.gridy = 3; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0;
        detailsPanel.add(new JLabel("Story Points:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0; detailGbc.anchor = GridBagConstraints.WEST;
        storyPointsField = new JTextField();
        storyPointsField.setPreferredSize(new Dimension(60, storyPointsField.getPreferredSize().height));
        storyPointsField.setMaximumSize(new Dimension(60, storyPointsField.getPreferredSize().height));
        detailsPanel.add(storyPointsField, detailGbc);
        
        formPanel.add(detailsPanel, gbc);
        
        add(formPanel, BorderLayout.CENTER);
        
        // Initially show "Select an issue" message
        clearIssueDetail();
    }
    
    private void setupResizeListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustComponentSizes();
            }
        });
    }
    
    private void adjustComponentSizes() {
        SwingUtilities.invokeLater(() -> {
            int panelWidth = getWidth();

            if (panelWidth > 0) {
                // Calculate available width for components (considering margins and padding)
                int availableWidth = panelWidth - 40; // Account for margins and padding

                // Limit maximum width to prevent too wide fields
                int fieldWidth = Math.min(availableWidth, 200);
                int comboWidth = Math.min(availableWidth, 200);

                // Adjust text fields
                if (issueKeyField != null) {
                    issueKeyField.setPreferredSize(new Dimension(fieldWidth, issueKeyField.getPreferredSize().height));
                }
                if (issueSummaryField != null) {
                    issueSummaryField.setPreferredSize(new Dimension(availableWidth, issueSummaryField.getPreferredSize().height));
                }
                if (issueStatusComboBox != null) {
                    issueStatusComboBox.setPreferredSize(new Dimension(comboWidth, issueStatusComboBox.getPreferredSize().height));
                }
                if (storyPointsField != null) {
                    storyPointsField.setPreferredSize(new Dimension(60, storyPointsField.getPreferredSize().height));
                }
                
                // Adjust description area to fit available width
                if (descriptionScrollPane != null && issueDescriptionField != null) {
                    int descriptionWidth = Math.max(300, availableWidth);
                    descriptionScrollPane.setPreferredSize(new Dimension(descriptionWidth, 90)); // Fixed height to prevent overflow\n                    descriptionScrollPane.setMaximumSize(new Dimension(descriptionWidth, 90)); // Enforce maximum height
                    issueDescriptionField.setColumns((descriptionWidth - 20) / 8); // Approximate character width
                }
                
                // Force revalidate and repaint
                revalidate();
                repaint();
            }
        });
    }
    
    public void populateIssueForm(JiraIssue issue) {
        issueKeyField.setText(issue.getKey() != null ? issue.getKey() : "");
        issueSummaryField.setText(issue.getSummary() != null ? issue.getSummary() : "");
        String description = issue.getDescription();
        if (description == null || description.trim().isEmpty()) {
            issueDescriptionField.setText("설명 편집");
            issueDescriptionField.setForeground(Color.GRAY);
        } else {
            issueDescriptionField.setText(description);
            issueDescriptionField.setForeground(UIManager.getColor("TextField.foreground"));
        }
        
        // Populate detail fields
        if (issue.getAssignee() != null && !issue.getAssignee().trim().isEmpty()) {
            assigneeLabel.setText(issue.getAssignee());
        } else {
            assigneeLabel.setText("할당되지 않음");
        }
        reporterLabel.setText(issue.getReporter() != null ? issue.getReporter() : "없음");
        
        // Populate Epic information
        if (issue.getParentKey() != null && issue.getParentSummary() != null) {
            String epicText = issue.getParentKey() + " " + issue.getParentSummary();
            epicLabel.setText(epicText);
            
            // Apply colored background if Epic color is available
            if (issue.getEpicColor() != null) {
                try {
                    Color epicColor = Color.decode(issue.getEpicColor());
                    epicLabel.setBackground(epicColor);
                    // Set text color based on background brightness
                    int brightness = (int) (0.299 * epicColor.getRed() + 0.587 * epicColor.getGreen() + 0.114 * epicColor.getBlue());
                    epicLabel.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);
                } catch (NumberFormatException e) {
                    // If color parsing fails, use default background
                    epicLabel.setBackground(UIManager.getColor("Label.background"));
                    epicLabel.setForeground(UIManager.getColor("Label.foreground"));
                }
            } else {
                // Use default Epic color
                epicLabel.setBackground(new Color(230, 230, 250)); // Light lavender
                epicLabel.setForeground(Color.BLACK);
            }
        } else {
            epicLabel.setText("없음");
            epicLabel.setBackground(UIManager.getColor("Label.background"));
            epicLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
        
        storyPointsField.setText(issue.getStoryPoints() != null ? issue.getStoryPoints().toString() : "");
        
        // Enable editing controls
        issueSummaryField.setEnabled(true);
        issueStatusComboBox.setEnabled(true);
        issueDescriptionField.setEnabled(true);
        assigneeLabel.setEnabled(true);
        epicLabel.setEnabled(true);
        storyPointsField.setEnabled(true);
        
        // Show hamburger menu when issue is loaded
        hamburgerMenuButton.setVisible(true);
    }
    
    public void clearIssueDetail() {
        issueKeyField.setText("이슈를 선택하세요");
        issueSummaryField.setText("");
        issueDescriptionField.setText("설명 편집");
        issueDescriptionField.setForeground(Color.GRAY);
        issueStatusComboBox.removeAllItems();
        
        // Clear detail fields
        assigneeLabel.setText("할당되지 않음");
        reporterLabel.setText("");
        epicLabel.setText("없음");
        epicLabel.setBackground(UIManager.getColor("Label.background"));
        epicLabel.setForeground(UIManager.getColor("Label.foreground"));
        storyPointsField.setText("");
        
        // Disable editing controls
        issueSummaryField.setEnabled(false);
        issueStatusComboBox.setEnabled(false);
        issueDescriptionField.setEnabled(false);
        assigneeLabel.setEnabled(false);
        epicLabel.setEnabled(false);
        storyPointsField.setEnabled(false);
        
        // Hide hamburger menu when no issue is selected
        hamburgerMenuButton.setVisible(false);
    }
    
    // Getters for components
    public JTextField getIssueSummaryField() { return issueSummaryField; }
    public JComboBox<String> getIssueStatusComboBox() { return issueStatusComboBox; }
    public JTextArea getIssueDescriptionField() { return issueDescriptionField; }
    public JTextField getStoryPointsField() { return storyPointsField; }
    public JLabel getAssigneeLabel() { return assigneeLabel; }
    public JLabel getEpicLabel() { return epicLabel; }
    public JButton getSaveDescriptionButton() { return saveDescriptionButton; }
    public JButton getCancelDescriptionButton() { return cancelDescriptionButton; }
    public JPanel getDescriptionButtonPanel() { return descriptionButtonPanel; }
    public JScrollPane getDescriptionScrollPane() { return descriptionScrollPane; }
    public JButton getHamburgerMenuButton() { return hamburgerMenuButton; }
    
    // Event handlers setters
    public void setOnHamburgerMenuClick(Consumer<Void> onHamburgerMenuClick) {
        this.onHamburgerMenuClick = onHamburgerMenuClick;
    }
    
    public void setOnAssigneeLabelClick(Consumer<Void> onAssigneeLabelClick) {
        this.onAssigneeLabelClick = onAssigneeLabelClick;
    }
    
    public void setOnEpicLabelClick(Consumer<Void> onEpicLabelClick) {
        this.onEpicLabelClick = onEpicLabelClick;
    }
    
    // Public method to trigger resize adjustment
    public void triggerResizeAdjustment() {
        adjustComponentSizes();
    }
}
