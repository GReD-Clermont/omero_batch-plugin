/*
 *  Copyright (C) 2021-2022 MICA & GReD
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.

 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package fr.igred.ij.gui;

import fr.igred.ij.macro.ProgressMonitor;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Container;
import java.awt.Font;

/**
 * Progress dialog for batch processing.
 */
public class ProgressDialog extends JFrame implements ProgressMonitor {
	private final JLabel progressLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel stateLabel = new JLabel("", SwingConstants.CENTER);
	private final JButton ok = new JButton("OK");


	/**
	 * Creates a new dialog.
	 */
	public ProgressDialog() {
		super.setTitle("Progression");
		super.setLocationRelativeTo(null);
		super.setSize(300, 200);

		Font warnFont = new Font("Arial", Font.PLAIN, 12);
		Font progFont = new Font("Arial", Font.BOLD, 12);
		JLabel warnLabel = new JLabel("", SwingConstants.CENTER);
		warnLabel
				.setText("<html> <body style='text-align:center;'> Warning: <br>Image processing can take time <br>depending on your network rate </body> </html>");
		warnLabel.setFont(warnFont);
		progressLabel.setFont(progFont);
		stateLabel.setFont(progFont);

		Container cp2 = super.getContentPane();
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


	/**
	 * Sets the current progress.
	 *
	 * @param text The text for the current progress.
	 */
	@Override
	public void setProgress(String text) {
		progressLabel.setText(text);
	}


	/**
	 * Sets the current state.
	 *
	 * @param text The text for the current state.
	 */
	@Override
	public void setState(String text) {
		stateLabel.setText(text);
	}


	/**
	 * Signals the process is done.
	 */
	@Override
	public void setDone() {
		setState("");
		setProgress("Task completed!");
		ok.setEnabled(true);
	}

}
