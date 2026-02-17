package net.sf.l2j.geodataconverter.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.geoengine.geodata.GeoFormat;
import net.sf.l2j.geodataconverter.GeoDataConverterEngine;
import net.sf.l2j.launcher.etc.Thema;

public final class GeoDataConverterUI extends JFrame
{
	private static final long serialVersionUID = 1L;
	
	private final JComboBox<String> cmbFormat = new JComboBox<>(new String[]
	{
		"L2J (.l2j)",
		"L2OFF (.dat)"
	});
	private final JTextArea logArea = new JTextArea();
	private final JProgressBar progress = new JProgressBar(0, 100);
	
	private final JLabel lblPath = new JLabel();
	private File geodataDir;
	
	private final JButton btnChoose = new JButton("Escolher pasta...");
	private final JButton btnOpen = new JButton("Abrir pasta");
	private final JButton btnStart = new JButton("Converter");
	private final JButton btnStop = new JButton("Parar");
	private final JButton btnClear = new JButton("Limpar log");
	
	private SwingWorker<GeoDataConverterEngine.Result, String> worker;
	private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
	
	public static void main(String[] args)
	{
	    Config.loadGeodataConverter();

	    SwingUtilities.invokeLater(() -> {
	        try
	        {
	            // 1) aplica tokens ANTES do Nimbus
	        	Thema.getInstance().aplly();

	            // 2) força Nimbus (essas chaves são do Nimbus)
	            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
	            {
	                if ("Nimbus".equals(info.getName()))
	                {
	                    UIManager.setLookAndFeel(info.getClassName());
	                    break;
	                }
	            }
	        }
	        catch (Exception ignored)
	        {
	        }

	        GeoDataConverterUI ui = new GeoDataConverterUI();

	        // 3) garante update após criar
	        SwingUtilities.updateComponentTreeUI(ui);

	        ui.setVisible(true);
	    });
	}

	
	public GeoDataConverterUI()
	{
		
		super("GeoData Converter - BAN-L2JDEV");
		
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				if (isWorking())
				{
					int opt = JOptionPane.showConfirmDialog(GeoDataConverterUI.this, "Conversão em andamento. Deseja parar e sair?", "Confirmar saída", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (opt != JOptionPane.YES_OPTION)
						return;
					
					requestCancel();
				}
				dispose();
			}
		});
		// Set icons.
		final List<Image> icons = new ArrayList<>();
		icons.add(new ImageIcon(".." + File.separator + "images" + File.separator + "l2jdev_16x16.png").getImage());
		icons.add(new ImageIcon(".." + File.separator + "images" + File.separator + "l2jdev_32x32.png").getImage());
		applyDarkTheme();
		setMinimumSize(new Dimension(980, 620));
		setSize(1100, 720);
		setLocationRelativeTo(null); // center
		setResizable(true); // permite redimensionar
		setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH); // já pode abrir maximizado se quiser
		setIconImages(icons);
		// Estado inicial: pasta vem do Config.GEODATA_PATH
		geodataDir = new File(Config.GEODATA_PATH);
		lblPath.setText("Pasta: " + geodataDir.getAbsolutePath());
		
		buildUi();
		setWorking(false);
		appendInfo("Ready. Selecione o tipo e confirme a pasta.");
	}
	
	private void buildUi()
	{
		JPanel top = new JPanel(new BorderLayout(10, 10));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		row1.add(new JLabel("Formato:"));
		row1.add(cmbFormat);
		
		btnChoose.addActionListener(e -> chooseFolder());
		btnOpen.addActionListener(e -> openFolder());
		btnClear.addActionListener(e -> logArea.setText(""));
		
		row1.add(btnChoose);
		row1.add(btnOpen);
		row1.add(btnClear);
		
		JPanel row2 = new JPanel(new BorderLayout());
		row2.add(lblPath, BorderLayout.CENTER);
		
		top.add(row1, BorderLayout.NORTH);
		top.add(row2, BorderLayout.SOUTH);
		
		// Log
		logArea.setEditable(false);
		logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
		JScrollPane scroll = new JScrollPane(logArea);
		scroll.setBorder(BorderFactory.createTitledBorder("Log"));
		
		// Bottom controls
		JPanel bottom = new JPanel(new BorderLayout(10, 10));
		bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		progress.setStringPainted(true);
		
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		
		btnStart.addActionListener(e -> startConversion());
		btnStop.addActionListener(e -> requestCancel());
		
		actions.add(btnStop);
		actions.add(btnStart);
		
		bottom.add(progress, BorderLayout.CENTER);
		bottom.add(actions, BorderLayout.EAST);
		
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(scroll, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);
	}
	
	private void chooseFolder()
	{
		JFileChooser fc = new JFileChooser(geodataDir);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setDialogTitle("Selecione a pasta GEODATA (input e output)");
		fc.setApproveButtonText("Selecionar");
		
		int r = fc.showOpenDialog(this);
		if (r == JFileChooser.APPROVE_OPTION)
		{
			geodataDir = fc.getSelectedFile();
			lblPath.setText("Pasta: " + geodataDir.getAbsolutePath());
			appendInfo("Selected folder: " + geodataDir.getAbsolutePath());
		}
	}
	
	private void openFolder()
	{
		try
		{
			if (geodataDir != null && geodataDir.exists())
				Desktop.getDesktop().open(geodataDir);
		}
		catch (Exception e)
		{
			appendError("Não foi possível abrir a pasta: " + e.getMessage());
		}
	}
	
	private GeoFormat selectedFormat()
	{
		return (cmbFormat.getSelectedIndex() == 0) ? GeoFormat.L2J : GeoFormat.L2OFF;
	}
	
	private void startConversion()
	{
		if (isWorking())
			return;
		
		if (geodataDir == null || !geodataDir.exists() || !geodataDir.isDirectory())
		{
			JOptionPane.showMessageDialog(this, "Selecione uma pasta GEODATA válida.", "Pasta inválida", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		cancelFlag.set(false);
		setWorking(true);
		progress.setValue(0);
		
		appendInfo("==================================================");
		appendInfo("Start: format=" + selectedFormat() + " | folder=" + geodataDir.getAbsolutePath());
		
		GeoDataConverterEngine engine = new GeoDataConverterEngine();
		
		worker = new SwingWorker<>()
		{
			@Override
			protected GeoDataConverterEngine.Result doInBackground()
			{
				return engine.convertAll(geodataDir.getAbsolutePath(), selectedFormat(), (lvl, msg) -> publish(formatLine(lvl, msg)), (done, total) -> {
					int pct = (total <= 0) ? 0 : (int) Math.round((done * 100.0) / total);
					setProgress(Math.max(0, Math.min(100, pct)));
				}, cancelFlag);
			}
			
			@Override
			protected void process(java.util.List<String> chunks)
			{
				for (String s : chunks)
					logArea.append(s + "\n");
				
				logArea.setCaretPosition(logArea.getDocument().getLength());
			}
			
			@Override
			protected void done()
			{
				try
				{
					GeoDataConverterEngine.Result r = get();
					if (r.cancelled)
						appendWarn("Finished: CANCELLED | converted=" + r.converted + "/" + r.total);
					else
						appendInfo("Finished: OK | converted=" + r.converted + "/" + r.total);
				}
				catch (Exception e)
				{
					appendError("Failed: " + e.getMessage());
				}
				finally
				{
					setWorking(false);
				}
			}
		};
		
		worker.addPropertyChangeListener(evt -> {
			if ("progress".equals(evt.getPropertyName()))
			{
				progress.setValue((int) evt.getNewValue());
				progress.setString(progress.getValue() + "%");
			}
		});
		
		worker.execute();
	}
	
	private void requestCancel()
	{
		if (!isWorking())
			return;
		
		cancelFlag.set(true);
		appendWarn("Cancel requested...");
		if (worker != null)
			worker.cancel(true);
	}
	
	private boolean isWorking()
	{
		return btnStart.isEnabled() == false;
	}
	
	private void setWorking(boolean working)
	{
		btnStart.setEnabled(!working);
		btnChoose.setEnabled(!working);
		cmbFormat.setEnabled(!working);
		
		btnStop.setEnabled(working);
		btnOpen.setEnabled(!working); // evitar abrir enquanto escreve
		btnClear.setEnabled(!working);
	}
	
	/* ===================== LOG HELPERS ===================== */
	
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	private static String formatLine(GeoDataConverterEngine.Level lvl, String msg)
	{
		String t = LocalDateTime.now().format(TS);
		return String.format("[%s] %-5s %s", t, lvl.name(), msg);
	}
	
	private void appendInfo(String msg)
	{
		logArea.append(formatLine(GeoDataConverterEngine.Level.INFO, msg) + "\n");
	}
	
	private void appendWarn(String msg)
	{
		logArea.append(formatLine(GeoDataConverterEngine.Level.WARN, msg) + "\n");
	}
	
	private void appendError(String msg)
	{
		logArea.append(formatLine(GeoDataConverterEngine.Level.ERROR, msg) + "\n");
	}
	
	private static void applyDarkTheme()
	{
	    // Nimbus usa essas keys; tem que ser antes de setLookAndFeel(Nimbus)
	    UIManager.put("control", new Color(28, 30, 34));
	    UIManager.put("info", new Color(28, 30, 34));
	    UIManager.put("nimbusBase", new Color(18, 20, 24));
	    UIManager.put("nimbusBlueGrey", new Color(45, 48, 56));
	    UIManager.put("nimbusLightBackground", new Color(24, 26, 30));
	    UIManager.put("nimbusFocus", new Color(90, 140, 235));
	    UIManager.put("nimbusSelectionBackground", new Color(60, 90, 170));

	    UIManager.put("text", new Color(230, 234, 242));
	    UIManager.put("nimbusSelectedText", Color.WHITE);
	    UIManager.put("nimbusDisabledText", new Color(140, 148, 160));
	}


}
