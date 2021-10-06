package jsfmidiplayer;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class PlayerWindow extends JFrame {

	class IntTriple {
		final int x;
		final int y;
		final int z;

		IntTriple(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		int getX() {
			return this.x;
		}

		int getY() {
			return this.y;
		}

		int getZ() {
			return this.z;
		}

		@Override
		public String toString() {
			String retVal = ("" + this.x + "," + this.y + "");
			return retVal;
		}
	}

	class PlaybackSliderWorker extends SwingWorker<Object, Object> {

		@Override
		protected Object doInBackground() throws Exception {
			while (isPlaying) {

				if (!playbackSlider.getValueIsAdjusting()) {
					playbackSlider.setValue((int) (sequencer.getMicrosecondPosition() / 1000000));
					playbackSlider.repaint();
				}

				if (playbackSlider.getValueIsAdjusting()) {
					sequencer.setMicrosecondPosition(playbackSlider.getValue() * 1000000);
					playbackSlider.repaint();
				}

			}
			return null;
		}
	}

	ArrayList<IntTriple> bankProgramList;

	boolean isPlaying = false;

	boolean isPaused = false;

	final DefaultListModel<String> model;

	File soundfontFile;
	Instrument[] instrumentArr;
	JButton openFileButton = new JButton("Open File");
	JButton openSoundfontButton = new JButton("Open Soundfont");
	JButton playButton = new JButton("Play");

	JButton pauseButton = new JButton("Pause");

	JButton stopButton = new JButton("Stop");

	JLabel nowPlaying = new JLabel("Nothing playing.");

	JLabel sfName = new JLabel("Using default soundfont.");
	JList<String> instrumentList;

	JMenu fileMenu = new JMenu("File");
	JMenu instrumentMenu = new JMenu("Instrument");
	JMenuBar menuBar = new JMenuBar();

	JPanel playbackPanel = new JPanel();
	JPanel instrumentPanel = new JPanel();

	JScrollPane instrumentPane;

	JSlider playbackSlider = new JSlider();

	MidiDevice device;

	final PlaybackSliderWorker playbackSliderWorker;

	Sequence sequence;

	Sequencer sequencer;

	Soundbank sbNew;

	String lastFilePath;

	String[] gmInstrumentArr = new String[128];

	Synthesizer synth;

	Track[] trackArr;

	PlayerWindow() throws MidiUnavailableException {

		JMenu openMenu = new JMenu("Open");
		JMenuItem openMidi = new JMenuItem("MIDI File (.mid)");
		JMenuItem openSoundfont = new JMenuItem("Soundfont (sf2, .dls)");
		openMenu.add(openMidi).addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openMidiFile();
			}
		});
		openMenu.add(openSoundfont).addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openSoundfontFile();
			}
		});

		fileMenu.add(openMenu);
		menuBar.add(fileMenu);
		menuBar.add(instrumentMenu);

		HashMap<String, Integer> instrumentFamilies = new HashMap<String, Integer>();
		instrumentFamilies.put("Piano", 7);
		instrumentFamilies.put("Chromatic Percussion", 15);
		instrumentFamilies.put("Organ", 23);
		instrumentFamilies.put("Guitar", 31);
		instrumentFamilies.put("Bass", 39);
		instrumentFamilies.put("Strings", 47);
		instrumentFamilies.put("Ensemble", 55);
		instrumentFamilies.put("Brass", 63);
		instrumentFamilies.put("Reed", 71);
		instrumentFamilies.put("Pipe", 79);
		instrumentFamilies.put("Synth Lead", 87);
		instrumentFamilies.put("Synth Pad", 95);
		instrumentFamilies.put("Synth Effects", 103);
		instrumentFamilies.put("Ethnic", 111);
		instrumentFamilies.put("Percussive", 119);
		instrumentFamilies.put("Sound Effects", 127);

		instrumentFamilies.forEach((k, v) -> {
			JMenu instrumentFamily = new JMenu(k);
			for (int i = v - 7; i < (v + 1); i++) {
				int instrumentNum = i;
				String instrumentName = GMInstruments.values()[instrumentNum].toString();
				JMenuItem instrumentItem = new JMenuItem(instrumentName);
				instrumentItem.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						overrideInstrumentFromList(instrumentNum);
					}
				});
				instrumentFamily.add(instrumentItem);
			}
			instrumentMenu.add(instrumentFamily);
		});

		JMenuItem resetAll = new JMenuItem("Reset All Instruments");
		resetAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resetAllInstruments();
				System.out.println("Reset all instruments.");
			}
		});

		instrumentMenu.add(resetAll);

		setJMenuBar(menuBar);

		add(playbackPanel);
		add(instrumentPanel);

		setBounds(200, 200, 800, 600);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new FlowLayout());
		setTitle("Midi Player");
		setVisible(true);

		playbackPanel.add(playButton);
		playbackPanel.add(pauseButton);
		playbackPanel.add(stopButton);
		playbackPanel.add(playbackSlider);
		playbackPanel.add(nowPlaying);
		playbackPanel.add(sfName);
		playbackPanel.setBorder(BorderFactory.createTitledBorder("Playback"));
		playbackPanel.setPreferredSize(new Dimension(500, 100));
		playbackPanel.setVisible(true);

		playbackSlider.setEnabled(false);
		playbackSlider.setValue(0);
		playbackSlider.setVisible(true);
		playbackSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (isPlaying) {
					//
				}

			}
		});

		playbackSliderWorker = new PlaybackSliderWorker();
		model = new DefaultListModel<String>();

		instrumentList = new JList<String>(model);
		instrumentList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		instrumentPane = new JScrollPane(instrumentList);
		int instrumentCounter = 0;
		for (GMInstruments instrument : GMInstruments.values()) {
			gmInstrumentArr[instrumentCounter] = instrument.toString();
			instrumentCounter++;
		}

		instrumentPanel.add(instrumentPane);
		instrumentPanel.add(sfName);
		instrumentPane.setPreferredSize(new Dimension(475, 265));
		instrumentPanel.setBorder(BorderFactory.createTitledBorder("Instruments"));
		instrumentPanel.setPreferredSize(new Dimension(500, 325));
		instrumentPanel.setVisible(true);

		pauseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (sequencer.isOpen()) {
					if (!isPaused) {
						sequencer.stop();
						pauseButton.setText("Resume");
						isPaused = true;
					} else if (isPaused) {
						sequencer.start();
						pauseButton.setText("Pause");
						isPaused = false;
					}
				}
			}
		});

		device = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);

		playButton.setEnabled(false);
		playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				loadInstruments();
			}
		});

		stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (sequencer.isOpen()) {
					sequencer.stop();
				}
				sequencer.close();
				playbackSliderWorker.cancel(true);
			}
		});

		openSoundfontButton.setPreferredSize(new Dimension(125, 50));
		openSoundfontButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openSoundfontFile();
			}
		});

		openFileButton.setPreferredSize(new Dimension(100, 50));
		openFileButton.setVisible(true);
		openFileButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openMidiFile();
			}
		});

		DropTarget dt = new DropTarget() {
			public void drop(DropTargetDropEvent e) {
				e.acceptDrop(DnDConstants.ACTION_MOVE);
				try {

					if (sequencer != null) {
						if (sequencer.isOpen()) {
							sequencer.stop();
						}
						sequencer.setSequence(sequence);
						sequencer.close();
					}

					List<File> fileList = (List<File>) e.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
					File dropFile = fileList.get(0);
					System.out.println(dropFile.getName());
					sequence = null;
					sequence = MidiSystem.getSequence(dropFile);
					System.out.println("Length in ms: " + sequence.getMicrosecondLength());
					playButton.setEnabled(true);
					sequencer = MidiSystem.getSequencer();
					nowPlaying.setText(dropFile.getName());

				} catch (UnsupportedFlavorException | IOException | InvalidMidiDataException
						| MidiUnavailableException e1) {
					e1.printStackTrace();
				}
			}
		};
		this.setDropTarget(dt);
	}

	public void openSoundfontFile() {
		soundfontFile = null;
		JFileChooser soundfontChooser = new JFileChooser();
		soundfontChooser.setFileFilter(new FileNameExtensionFilter("Soundfonts (SF2, DLS)", "sf2", "dls"));
		soundfontChooser.getActionMap().get("viewTypeDetails").actionPerformed(null);
		soundfontChooser.setMultiSelectionEnabled(false);
		int retVal = soundfontChooser.showOpenDialog(null);
		if (retVal == JFileChooser.APPROVE_OPTION) {
			soundfontFile = soundfontChooser.getSelectedFile();
			sfName.setText(soundfontFile.getName());
		}
	}

	public void openMidiFile() {
		File openFile = null;
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI files", "mid", "midi"));
		fileChooser.setMultiSelectionEnabled(false);
		fileChooser.getActionMap().get("viewTypeDetails").actionPerformed(null);
		String desktopPath = System.getProperty("user.home") + "/Desktop";
		fileChooser.setCurrentDirectory(new File(desktopPath));
		int retVal = fileChooser.showOpenDialog(null);
		if (retVal == JFileChooser.APPROVE_OPTION) {
			openFile = fileChooser.getSelectedFile();
			lastFilePath = openFile.getAbsolutePath();
			playButton.setEnabled(true);
			if (sequencer != null) {
				if (sequencer.isOpen()) {
					sequencer.stop();
				}
				sequencer.close();
			}

			try {
				System.out.println(openFile.getName());
				sequence = null;
				sequence = MidiSystem.getSequence(openFile);
				nowPlaying.setText(openFile.getName());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	public void overrideInstrumentFromList(int overrideInstrument) {
		List<String> instrumentsToOverride = instrumentList.getSelectedValuesList();
		for (String instrumentToOverride : instrumentsToOverride) {
			System.out.println("You chose to override " + instrumentToOverride + " with " + overrideInstrument);
			int channelToOverride = Integer.parseInt(instrumentToOverride.split("Channel: ")[1]);
			ShortMessage smNew = new ShortMessage();
			try {
				smNew.setMessage(ShortMessage.PROGRAM_CHANGE, channelToOverride, overrideInstrument, 0);
				for (Track track : trackArr) {
					track.add(new MidiEvent(smNew, 0));
					for (int instrument : instrumentList.getSelectedIndices()) {
						String currentInstrument = model.getElementAt(instrument);
						int currentChannel = Integer.parseInt(currentInstrument.split("Channel: ")[1]);
						if (!currentInstrument.contains("Drumkit")) {
							model.setElementAt(GMInstruments.values()[overrideInstrument].toString()
									+ " (Override): Channel: " + currentChannel, instrument);
						}
					}
				}
			} catch (InvalidMidiDataException e1) {
				e1.printStackTrace();
			}
		}
		sequencer.setMicrosecondPosition(sequencer.getMicrosecondPosition()); // Somehow setting the position
																				// forces the instrument change
	}

	public void resetAllInstruments() {
		long currentPosition = sequencer.getMicrosecondPosition();
		if (true) {
			File openFile = new File(lastFilePath); // Reload the original file as if we
			playButton.setEnabled(true); // opened another one.
			if (sequencer != null) {
				if (sequencer.isOpen()) {
					sequencer.stop();
				}
				sequencer.close();
			}

			try {
				System.out.println(openFile.getName());
				sequence = null;
				sequence = MidiSystem.getSequence(openFile);
				nowPlaying.setText(openFile.getName());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		playButton.doClick();
		sequencer.setMicrosecondPosition(currentPosition);
	}

	public void loadInstruments() {

		isPlaying = true;

		long tickLength = sequence.getTickLength();
		long msLength = sequence.getMicrosecondLength();
		float minLength = (msLength / (float) 60000000);
		System.out.println("MIDI tick length: " + tickLength);
		System.out.println("Microsecond length: " + msLength);
		System.out.println(minLength + " minutes");

		bankProgramList = new ArrayList<IntTriple>();

		trackArr = sequence.getTracks();

		model.removeAllElements(); // Clear instrument list to get rid of override instruments that don't go
									// away.

		for (int i = 0; i < trackArr.length; i++) {
			for (int k = 0; k < trackArr[i].size(); k++) {
				MidiMessage message = trackArr[i].get(k).getMessage();
				if (message instanceof ShortMessage) {
					ShortMessage smOld = (ShortMessage) message;
					if (smOld.getCommand() == (ShortMessage.PROGRAM_CHANGE)) {

						int bank = smOld.getData2();
						int program = smOld.getData1();
						int channel = smOld.getChannel();

						IntTriple bankProgram = new IntTriple(bank, program, channel);
						bankProgramList.add(bankProgram);
					}
				}
			}
			try {
				ShortMessage smNoteOff = new ShortMessage(ShortMessage.NOTE_OFF, 0, 0, 0); // Workaround for
																							// that stuck
																							// instrument that
																							// happens on
																							// channel 0
				trackArr[i].add(new MidiEvent(smNoteOff, 0));
			} catch (InvalidMidiDataException e1) {
				e1.printStackTrace();
			}
		}

		try {
			if (soundfontFile != null) {
				if (sequencer != null) {
					if (sequencer.isOpen()) {
						sequencer.close();
						sequencer = null;
					}
				}
				if (synth != null) {
					if (synth.isOpen()) {
						synth.close();
						synth = null;
					}
				}

				Runtime.getRuntime().gc();
				synth = MidiSystem.getSynthesizer();
				sequencer = MidiSystem.getSequencer();
				sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
				sequencer.open();
				synth.open();
				Soundbank sbDefault = synth.getDefaultSoundbank();
				synth.unloadAllInstruments(sbDefault);
				if (sbNew != null) {
					synth.unloadAllInstruments(sbNew);
					sbNew = null;
				}
				sbNew = MidiSystem.getSoundbank(soundfontFile);

				synth.loadAllInstruments(sbNew);

				sequencer.getTransmitter().setReceiver(synth.getReceiver());
				sequencer.setSequence(sequence);
				sequencer.start();
				System.out.println("BPM: " + sequencer.getTempoInBPM());

			} else {
				sequencer = MidiSystem.getSequencer();
				synth = MidiSystem.getSynthesizer();

				sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
				sequencer.open();
				sequencer.setSequence(sequence);
				sequencer.start();
				instrumentArr = synth.getDefaultSoundbank().getInstruments();
				System.out.println("BPM: " + sequencer.getTempoInBPM());
			}

			playbackSlider.setEnabled(true);
			playbackSlider.setMaximum((int) (sequencer.getMicrosecondLength() / 1000000));
			playbackSliderWorker.execute();

			if (soundfontFile != null) {
				instrumentArr = synth.getLoadedInstruments();
			}

			for (int i = 0; i < instrumentArr.length; i++) {
				for (IntTriple intTriple : bankProgramList) {

					int bank = intTriple.getX();
					int program = intTriple.getY();
					int channel = intTriple.getZ();
					int instrumentBank = 0;
					int instrumentProgram = 0;
					String instrumentStr = instrumentArr[i].toString();
					String[] instrumentStrSplit = instrumentStr.split("#");

					instrumentProgram = Integer.parseInt(instrumentStrSplit[2]);
					instrumentBank = Integer.parseInt(instrumentStrSplit[1].replace(" preset", "").replace(" ", ""));

					if ((bank == instrumentBank) && (program == instrumentProgram)) {
						String instrumentName = instrumentArr[i].toString();
						if (instrumentName.contains("Drumkit")) {
							model.remove(0); // Remove the Piano instruments that are duplicates of the Drumkit
						}

						if (!model.contains(instrumentName + ", Channel: " + channel)) {
							model.addElement(instrumentName + ", Channel: " + channel);
							System.out.println("Found Match: " + instrumentName + ": Channel: " + channel);
						}
					}
				}
			}

		} catch (Exception e2) {
			e2.printStackTrace();
		}
	}
}
