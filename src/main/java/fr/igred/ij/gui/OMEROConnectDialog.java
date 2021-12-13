package fr.igred.ij.gui;

import fr.igred.omero.Client;
import fr.igred.omero.exception.ServiceException;
import ij.Prefs;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Connection dialog for OMERO.
 */
public class OMEROConnectDialog extends JDialog implements ActionListener {

	private final JTextField hostField = new JTextField("");
	private final JFormattedTextField portField = new JFormattedTextField(NumberFormat.getIntegerInstance());
	private final JTextField userField = new JTextField("");
	private final JPasswordField passwordField = new JPasswordField("");
	private final JButton login = new JButton("Login");
	private final JButton cancel = new JButton("Cancel");
	private transient Client client;
	private boolean cancelled = false;


	/**
	 * Creates a new dialog to connect the specified client, but does not display it.
	 */
	public OMEROConnectDialog() {
		super();

		final int width = 350;
		final int height = 200;

		final String defaultHost = "bioimage.france-bioinformatique.fr";
		final int defaultPort = 4064;

		super.setModal(true);
		super.setTitle("Connection to OMERO");
		super.setSize(width, height);
		super.setMinimumSize(new Dimension(width, height));
		super.setLocationRelativeTo(null); // center the window

		String host = Prefs.get("omero.host", defaultHost);
		long port = Prefs.getInt("omero.port", defaultPort);
		String username = Prefs.get("omero.user", "");

		Container cp = super.getContentPane();
		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));

		JPanel panelInfo = new JPanel();
		panelInfo.setLayout(new BoxLayout(panelInfo, BoxLayout.LINE_AXIS));

		JPanel panelInfo1 = new JPanel();
		panelInfo1.setLayout(new GridLayout(4, 1, 0, 3));

		JPanel panelInfo2 = new JPanel();
		panelInfo2.setLayout(new GridLayout(4, 1, 0, 3));
		panelInfo1.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		panelInfo2.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

		JLabel hostLabel = new JLabel("Host:");
		panelInfo1.add(hostLabel);
		panelInfo2.add(hostField);
		hostField.setText(host);

		JLabel portLabel = new JLabel("Port:");
		panelInfo1.add(portLabel);
		panelInfo2.add(portField);
		portField.setValue(port);

		JLabel userLabel = new JLabel("User:");
		panelInfo1.add(userLabel);
		panelInfo2.add(userField);
		userField.setText(username);

		JLabel passwdLabel = new JLabel("Password:");
		panelInfo1.add(passwdLabel);
		panelInfo2.add(passwordField);

		JPanel buttons = new JPanel();
		buttons.add(cancel);
		buttons.add(login);

		panelInfo.add(panelInfo1);
		panelInfo.add(panelInfo2);
		cp.add(panelInfo);
		cp.add(buttons);

		super.getRootPane().setDefaultButton(login);
		super.setVisible(false);
	}


	/**
	 * Displays the login window and connects the client.
	 *
	 * @param c The client.
	 */
	public void connect(Client c) {
		this.client = c;
		login.addActionListener(this);
		cancel.addActionListener(this);
		this.setVisible(true);
	}


	/**
	 * Invoked when an action occurs.
	 *
	 * @param e the event to be processed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == login) {
			cancelled = false;
			String host = this.hostField.getText();
			Long port = (Long) this.portField.getValue();
			String username = this.userField.getText();
			char[] password = this.passwordField.getPassword();
			try {
				client.connect(host, port.intValue(), username, password);
				Prefs.set("omero.host", host);
				Prefs.set("omero.port", port.intValue());
				Prefs.set("omero.user", username);
				dispose();
			} catch (ServiceException e1) {
				String errorValue = e1.getCause().getMessage();
				String message = e1.getCause().getMessage();
				if ("Login credentials not valid".equals(errorValue)) {
					message = "Login credentials not valid";
				} else if (String.format("Can't resolve hostname %s", host).equals(errorValue)) {
					message = "Hostname not valid";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			} catch (RuntimeException e1) {
				String errorValue = e1.getMessage();
				String message = e1.getMessage();
				if ("Obtained null object proxy".equals(errorValue)) {
					message = "Port not valid or no internet access";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		} else if (source == cancel) {
			cancelled = true;
			dispose();
		}
	}


	/**
	 * Specifies if cancel button was chosen.
	 *
	 * @return True if cancel was pressed.
	 */
	public boolean wasCancelled() {
		return cancelled;
	}

}