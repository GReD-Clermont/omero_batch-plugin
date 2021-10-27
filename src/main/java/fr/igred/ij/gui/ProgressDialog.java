package fr.igred.ij.gui;

import fr.igred.ij.macro.ProgressMonitor;

import javax.swing.*;
import java.awt.Container;
import java.awt.Font;


public class ProgressDialog extends JFrame implements ProgressMonitor {
	private final JLabel progressLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel stateLabel = new JLabel("", SwingConstants.CENTER);
	private final JButton ok = new JButton("OK");


	public ProgressDialog() {
		this.setTitle("Progression");
		this.setLocationRelativeTo(null);
		this.setSize(300, 200);

		Font warnFont = new Font("Arial", Font.PLAIN, 12);
		Font progFont = new Font("Arial", Font.BOLD, 12);
		JLabel warnLabel = new JLabel("", SwingConstants.CENTER);
		warnLabel
				.setText("<html> <body style='text-align:center;'> Warning: <br>Image processing can take time <br>depending on your network rate </body> </html>");
		warnLabel.setFont(warnFont);
		progressLabel.setFont(progFont);
		stateLabel.setFont(progFont);

		Container cp2 = this.getContentPane();
		cp2.setLayout(new BoxLayout(cp2, BoxLayout.PAGE_AXIS));
		JPanel panel0 = new JPanel();
		panel0.add(warnLabel);
		JPanel panel1 = new JPanel();
		panel1.add(progressLabel);
		JPanel panel2 = new JPanel();
		panel2.add(stateLabel);
		JPanel panel3 = new JPanel();
		panel3.add(ok);
		ok.setEnabled(false);
		ok.addActionListener(e -> dispose());
		cp2.add(panel0);
		cp2.add(panel1);
		cp2.add(panel2);
		cp2.add(panel3);
	}


	@Override
	public void setProgress(String text) {
		progressLabel.setText(text);
	}


	@Override
	public void setState(String text) {
		stateLabel.setText(text);
	}


	@Override
	public void setDone() {
		setState("");
		setProgress("Task completed!");
		ok.setEnabled(true);
	}

}
