package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.protocol.MessageObserver;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Protocol message inspector: live timeline of the frames exchanged
 * between the monitor client and the server.
 */
final class MessagesPage extends PagePanel {

    private final MessageLogModel model;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel = new JLabel("已捕获 0 条");
    private final JButton pauseButton = UiStyles.secondaryButton("暂停显示");
    private final Consumer<MessageEvent> modelListener;

    private boolean paused;
    private int captured;

    MessagesPage(DashboardFrame frame, MessageLogModel model) {
        super(frame);
        this.model = model;
        tableModel = new DefaultTableModel(
                new Object[] {"时间", "方向", "消息类型", "大小"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setFont(UiStyles.FONT.deriveFont(12f));
        table.getTableHeader().setFont(UiStyles.SMALL.deriveFont(Font.BOLD, 12f));
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(240);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable parent, Object value, boolean selected,
                    boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        parent, value, selected, focused, row, column);
                if (!selected) {
                    component.setForeground(column == 1
                            ? ("↑ 发送".equals(value) ? UiStyles.GREEN : UiStyles.MUTED)
                            : UiStyles.TEXT);
                }
                setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                return component;
            }
        });

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titleColumn = new JPanel();
        titleColumn.setOpaque(false);
        titleColumn.setLayout(new BoxLayout(titleColumn, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("协议报文观测");
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "监控端与服务器之间的 JSON 行协议实时帧（AUTH / 快照 / 告警 / 查询）");
        subtitle.setFont(UiStyles.SMALL);
        subtitle.setForeground(UiStyles.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleColumn.add(title);
        titleColumn.add(Box.createVerticalStrut(3));
        titleColumn.add(subtitle);
        header.add(titleColumn, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        JButton clearButton = UiStyles.secondaryButton("清空");
        pauseButton.addActionListener(this::togglePause);
        clearButton.addActionListener(event -> clear());
        actions.add(pauseButton);
        actions.add(clearButton);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiStyles.BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        status.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        statusLabel.setFont(UiStyles.SMALL);
        statusLabel.setForeground(UiStyles.MUTED);
        status.add(statusLabel, BorderLayout.WEST);
        add(status, BorderLayout.SOUTH);

        modelListener = event -> SwingUtilities.invokeLater(() -> onEvent(event));
        model.addListener(modelListener);
    }

    private void onEvent(MessageEvent event) {
        captured++;
        if (!paused) {
            appendRow(event);
        }
        updateStatus();
    }

    private void appendRow(MessageEvent event) {
        tableModel.addRow(new Object[] {
            UiStyles.formatTime(event.timestampMillis()),
            event.direction() == MessageObserver.Direction.SENT ? "↑ 发送" : "↓ 接收",
            event.type(),
            event.bytes() + " B"
        });
        SwingUtilities.invokeLater(() -> {
            int last = tableModel.getRowCount() - 1;
            if (last >= 0) {
                table.scrollRectToVisible(table.getCellRect(last, 0, true));
            }
        });
    }

    private void togglePause(ActionEvent ignored) {
        paused = !paused;
        pauseButton.setText(paused ? "继续显示" : "暂停显示");
        if (!paused) {
            tableModel.setRowCount(0);
            List<MessageEvent> backlog = model.snapshot();
            for (MessageEvent event : backlog) {
                tableModel.addRow(new Object[] {
                    UiStyles.formatTime(event.timestampMillis()),
                    event.direction() == MessageObserver.Direction.SENT ? "↑ 发送" : "↓ 接收",
                    event.type(),
                    event.bytes() + " B"
                });
            }
        }
        updateStatus();
    }

    private void clear() {
        model.clear();
        captured = 0;
        tableModel.setRowCount(0);
        updateStatus();
    }

    private void updateStatus() {
        statusLabel.setText("已捕获 " + captured + " 条"
                + (paused ? "（显示已暂停，继续后补全）" : ""));
    }
}
