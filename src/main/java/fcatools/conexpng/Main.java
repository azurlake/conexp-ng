package fcatools.conexpng;

import com.alee.laf.WebLookAndFeel;
import com.google.common.collect.Sets;
import de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException;
import de.tudresden.inf.tcs.fcalib.FullObject;
import fcatools.conexpng.gui.MainFrame;
import fcatools.conexpng.gui.lattice.LatticeGraph;
import fcatools.conexpng.io.CEXReader;
import fcatools.conexpng.model.FormalContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.Properties;

/**
 * 
 * The code for (re)storing the window location is from: <href
 * a="http://stackoverflow.com/a/7778332/283607">What is the best practice for
 * setting JFrame locations in Java?</a>
 * 
 */
public class Main {

	public static final String settingsDirName = ".conexp-ng";
	public static final String optionsFileName = new File(getSettingsDirectory(), "options.prop").getPath();
	private static Rectangle r;
	private static Exception exception = null;
	private static boolean fileOpened = false;

	public Main() {
		WebLookAndFeel.install();

		// Disable border around focused cells as it does not fit into the
		// context editor concept
		UIManager.put("Table.focusCellHighlightBorder", new EmptyBorder(0, 0, 0, 0));
		// Disable changing foreground color of cells as it does not fit into
		// the context editor concept
		UIManager.put("Table.focusCellForeground", Color.black);

		final Conf state = new Conf();

		boolean firstStart = false;
		File optionsFile = new File(optionsFileName);
		if (optionsFile.exists()) {
			try {
				restoreOptions(state);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} else {
			showExample(state);
			state.filePath = System.getProperty("user.home") + System.getProperty("file.separator") + "untitled.cex";
			firstStart = true;
		}

		// Create main window and take care of correctly saving and restoring
		// the last window location
		final MainFrame f = new MainFrame(state);
		f.setTitle("ConExp-NG - \"" + state.filePath + "\"");
		f.setMinimumSize(new Dimension(1000, 660));
		if (exception != null) {
			Util.handleIOExceptions(f, exception, state.filePath);
		}
		if (firstStart) {
			f.setSize(1000, 660);
			f.setLocationByPlatform(true);
			state.contextChanged();
		} else
			f.setBounds(r);
		f.setVisible(true);

		// Force various GUI components to update
		if (fileOpened)
			state.loadedFile();
		else
			state.contextChanged();
		state.saveConf();
	}

	public static void main(String... args) {
		new Main();
	}

	private void showExample(Conf state) {
		FormalContext context = new FormalContext();
		context.addAttribute("female");
		context.addAttribute("juvenile");
		context.addAttribute("adult");
		context.addAttribute("male");
		try {
			context.addObject(new FullObject<>("girl", Sets.newHashSet("female", "juvenile")));
			context.addObject(new FullObject<>("woman", Sets.newHashSet("female", "adult")));
			context.addObject(new FullObject<>("boy", Sets.newHashSet("male", "juvenile")));
			context.addObject(new FullObject<>("man", Sets.newHashSet("male", "adult")));
		} catch (IllegalObjectException e1) {
			e1.printStackTrace();
		}
		state.newContext(context);
        state.lattice = new LatticeGraph();
	}

	// Store location & size of UI & dir that was last opened from
	public static void storeOptions(Frame f, Conf state) throws Exception {
		File file = new File(optionsFileName);
		Properties p = new Properties();
		// restore the frame from 'full screen' first!
		f.setExtendedState(Frame.NORMAL);
		Rectangle r = f.getBounds();
		int x = (int) r.getX();
		int y = (int) r.getY();
		int w = (int) r.getWidth();
		int h = (int) r.getHeight();

		p.setProperty("x", "" + x);
		p.setProperty("y", "" + y);
		p.setProperty("w", "" + w);
		p.setProperty("h", "" + h);
		if (new File(state.filePath).exists())
			p.setProperty("lastOpened", state.filePath);
		else if (!state.lastOpened.isEmpty())
			p.setProperty("lastOpened", state.lastOpened.firstElement());
		else
			p.setProperty("lastOpened", "");
		for (int i = 0; i < 5; i++) {
			if (state.lastOpened.size() > i)
				p.setProperty("lastOpened" + i, state.lastOpened.get(i));
			else
				p.setProperty("lastOpened" + i, "");
		}
		BufferedWriter br = new BufferedWriter(new FileWriter(file));
		p.store(br, "Properties of the user frame");
	}

	// Restore location & size of UI & dir that was last opened from
	private void restoreOptions(Conf state) throws IOException {
		File file = new File(optionsFileName);
		Properties p = new Properties();
		BufferedReader br = new BufferedReader(new FileReader(file));
		p.load(br);

		int x = Integer.parseInt(p.getProperty("x"));
		int y = Integer.parseInt(p.getProperty("y"));
		int w = Integer.parseInt(p.getProperty("w"));
		int h = Integer.parseInt(p.getProperty("h"));
		String lastOpened = p.getProperty("lastOpened");
		if (p.containsKey("lastOpened0")) {
			for (int i = 0; i < 5; i++) {
				if (p.getProperty("lastOpened" + i).isEmpty())
					break;
				state.lastOpened.add(p.getProperty("lastOpened" + i));
			}
		}
		if (new File(lastOpened).isFile()) {
			state.filePath = lastOpened;
			try {
				new CEXReader(state, state.filePath);
				fileOpened = true;
			} catch (Exception e) {
				exception = e;
			}
		} else {
			showExample(state);
			state.filePath = System.getProperty("user.home") + System.getProperty("file.separator") + "untitled.cex";
		}
		r = new Rectangle(x, y, w, h);
	}

	// http://stackoverflow.com/a/193987/283607
	private static File getSettingsDirectory() {
		String userHome = System.getProperty("user.home");
		if (userHome == null) {
			throw new IllegalStateException("user.home==null");
		}
		File home = new File(userHome);
		File settingsDirectory = new File(home, settingsDirName);
		if (!settingsDirectory.exists()) {
			if (!settingsDirectory.mkdir()) {
				throw new IllegalStateException(settingsDirectory.toString());
			}
		}
		return settingsDirectory;
	}

}
