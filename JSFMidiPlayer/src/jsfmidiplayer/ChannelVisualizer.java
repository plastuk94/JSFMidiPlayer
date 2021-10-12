package jsfmidiplayer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class ChannelVisualizer extends JPanel {

	private MidiChannel[] channelArr;
	private Synthesizer synth;
	private ArrayList<ChannelBar> channelBarList;
	private VisualizerThread visualizerThread;

	protected int barWidth;
	protected int barNum = 16;

	protected Boolean gotMessage = false;

	ChannelVisualizer() {
	}

	public void start() throws MidiUnavailableException {

		synth = MidiSystem.getSynthesizer();
		channelArr = synth.getChannels();
		channelBarList = new ArrayList<ChannelBar>();

		if (barNum < 10) { // Less than 10 bars
			barNum = 10;   // seems to hide the drum track.
		}
		if (barNum > 16) { // Never need more than 16
			barNum = 16;   // MIDI channels.
		}
		
		barWidth = ((getWidth() / barNum) - 10);

		int x = 5;
		int y = 5;

		for (MidiChannel channel : channelArr) {
			ChannelBar bar = new ChannelBar(channel, x, y, barWidth, 0);
			x += (barWidth + 10);
			channelBarList.add(bar);
		}

		visualizerThread = new VisualizerThread(this);
		visualizerThread.execute();
	}

	protected int getBarNum() {
		return barNum;
	}

	protected void setBarNum(int barNum) {
		this.barNum = barNum;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		g2.clearRect(0, 0, super.getWidth(), super.getHeight());
		
		AffineTransform transform = g2.getTransform();
		
		transform.rotate(Math.toRadians(180)); // Flip 180 degrees around 0,0 (top left corner)
		transform.scale(-1, 1); // Flip across X axis
		transform.translate(0, (-1 * super.getHeight())); // Translate downward to bottom left
		g2.setTransform(transform);

		for (ChannelBar bar : channelBarList) {
			int height = bar.getHeight();
			g2.setColor(new Color((height * 2), (255 - height), 0 ));
			g2.fillRect(bar.getX(), bar.getY(), bar.getWidth(), height);
	
			if (height > 0) {
				bar.setHeight(height - 1);
			}
		}
	}

	public VisualizerThread getVisualizerThread() {
		return this.visualizerThread;
	}

	private class ChannelBar {
		protected MidiChannel channel;
		protected Color color;
		private int x;
		private int y;
		private int width;
		private int height;

		ChannelBar(MidiChannel channel, int x, int y, int width, int height) {
			this.channel = channel;
			this.x = x;
			this.y = y;
			this.height = height;
			this.width = width;
		}

		protected MidiChannel getChannel() {
			return channel;
		}

		protected void setChannel(MidiChannel channel) {
			this.channel = channel;
		}

		protected Color getColor() {
			return color;
		}

		protected void setColor(Color color) {
			this.color = color;
		}

		protected int getX() {
			return x;
		}

		protected void setX(int x) {
			this.x = x;
		}

		protected int getY() {
			return y;
		}

		protected void setY(int y) {
			this.y = y;
		}

		protected int getWidth() {
			return width;
		}

		protected void setWidth(int width) {
			this.width = width;
		}

		protected int getHeight() {
			return height;
		}

		protected void setHeight(int height) {
			this.height = height;
		}
	}

	private class ChannelBarButton extends JButton {
		protected boolean active = false;
		public static final int MUTE = 0;
		public static final int SOLO = 1;
		private final int MODE;
		private MidiChannel channel;

		ChannelBarButton(ChannelBar channelBar, int mode) throws MidiUnavailableException {
			this.MODE = mode;
			channel = channelBar.channel;

			this.setBounds(channelBar.getX(), (channelBar.getY() - channelBar.getHeight() - 5), channelBar.getWidth(),
					20);

			switch (this.MODE) {
			case ChannelBarButton.MUTE:
				this.setText("Mute");
				this.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if (!active) {
							channel.setMute(true);
							active = true;
							setText("Unmute");
						} else {
							channel.setMute(false);
							active = false;
							setText("Mute");
						}
					}
				});
				break;

			case ChannelBarButton.SOLO:
				this.setText("Solo");
				this.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if (!active) {
							channel.setSolo(true);
							active = true;
							setText("Solo Off");
						} else if (active) {
							channel.setSolo(false);
							active = false;
							setText("Solo");
						}
					}
				});
				break;

			default:
				throw new IllegalArgumentException(
						"Invalid mode: must be either ChannelBarButton.MUTE" + " or ChannelBarButton.SOLO");
			}
		}
	}

	private class VisualizerThread extends SwingWorker<Object, Object> implements MetaEventListener {
		ChannelVisualizer channelVisualizer;

		VisualizerThread(ChannelVisualizer channelVisualizer) {
			this.channelVisualizer = channelVisualizer;
		}

		@Override
		protected Object doInBackground() throws Exception {
			while (!this.isCancelled()) {
				channelVisualizer.repaint();

			}
			return null;
		}

		@Override
		public void meta(MetaMessage meta) {
			byte[] metadata = meta.getMessage();
			int channelNum;
			

			switch ((int) metadata[1]) {

			case 1:
				// Volume Change message
				channelNum = (metadata[3] + 80);
				int volumeValue = metadata[5];
				channelBarList.get(channelNum).setHeight(volumeValue);
				//System.out.println("Volume Change:");
				//System.out.println(metadata[1]+","+metadata[2]+","+channelNum+","+metadata[4]+","+metadata[5]);
				break;
			case 2:
				// Note On message
				channelNum = (metadata[3] + 112);
				//int pitch = metadata[4];
				int velocity = metadata[5];
				channelBarList.get(channelNum).setHeight(velocity);
				//System.out.println("Note On:");
				//System.out.println(metadata[1]+","+metadata[2]+","+channelNum+","+metadata[4]+","+metadata[5]);

				break;
			case 3:
				// Note Off message
				//TODO Implement NoteOff handling
				//System.out.println("Note Off:");
				break;
			}
			
		}

	}
}
