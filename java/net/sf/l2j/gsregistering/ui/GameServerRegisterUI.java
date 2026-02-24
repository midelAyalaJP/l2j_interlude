package net.sf.l2j.gsregistering.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.launcher.etc.Thema;
import net.sf.l2j.loginserver.GameServerTable;
import net.sf.l2j.loginserver.GameServerTable.GameServerInfo;

public final class GameServerRegisterUI extends JFrame
{
	private static final long serialVersionUID = 1L;
	
	// destinos finais (ajuste se sua estrutura for diferente)
	private static final Path GAME_HEX_PATH = Paths.get("../game/config/other/hexid.txt").normalize();
	
	private final DefaultTableModel model = new DefaultTableModel(new Object[]
	{
		"ID",
		"Nome",
		"Status"
	}, 0)
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public boolean isCellEditable(int row, int col)
		{
			return false;
		}
	};
	
	private final JTable table = new JTable(model);
	
	private final JComboBox<Integer> cmbIds = new JComboBox<>();
	private final JButton btnRefresh = new JButton("Atualizar lista");
	private final JButton btnRegister = new JButton("Registrar ID");
	private final JButton btnClean = new JButton("Limpar ID selecionado");
	private final JButton btnCleanAll = new JButton("Limpar TODOS");
	private final JButton btnOpenGameConfig = new JButton("Abrir game/config");
	
	private final JButton btnClearLog = new JButton("Limpar log");
	
	private final JTextArea logArea = new JTextArea();
	private final JLabel lblOut = new JLabel();
	
	private SwingWorker<Void, String> worker;
	private final AtomicBoolean busy = new AtomicBoolean(false);
	
	// evita loop combo<->tabela
	private volatile boolean _syncingSelection = false;
	
	public static void main(String[] args)
	{
		Config.loadGameServerRegistration();
		
		SwingUtilities.invokeLater(() -> {
			try
			{
				Thema.getInstance().aplly();
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
			
			GameServerRegisterUI ui = new GameServerRegisterUI();
			SwingUtilities.updateComponentTreeUI(ui);
			ui.setVisible(true);
		});
	}
	
	public GameServerRegisterUI()
	{
		super("GameServer Register");
		
		final List<Image> icons = new ArrayList<>();
		icons.add(new ImageIcon(".." + File.separator + "images" + File.separator + "l2jdev_16x16.png").getImage());
		icons.add(new ImageIcon(".." + File.separator + "images" + File.separator + "l2jdev_32x32.png").getImage());
		
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(980, 620));
		setSize(1100, 720);
		setLocationRelativeTo(null);
		setResizable(true);
		setIconImages(icons);
		
		buildUi();
		refreshTable();
		
		appendInfo("Ready. Clique em um server na tabela ou selecione no combo e use Registrar / Limpar.");
	}
	
	private void buildUi()
	{
		// ===== Top =====
		JPanel top = new JPanel(new BorderLayout(10, 10));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		row1.add(new JLabel("ID:"));
		cmbIds.setPreferredSize(new Dimension(140, 28));
		row1.add(cmbIds);
		
		btnRefresh.addActionListener(e -> refreshTable());
		btnRegister.addActionListener(e -> registerSelected());
		btnClean.addActionListener(e -> cleanSelected());
		btnCleanAll.addActionListener(e -> cleanAll());
		btnOpenGameConfig.addActionListener(e -> openFolderSafe(GAME_HEX_PATH.getParent()));
		
		btnClearLog.addActionListener(e -> logArea.setText(""));
		
		row1.add(btnRefresh);
		row1.add(btnRegister);
		row1.add(btnClean);
		row1.add(btnCleanAll);
		row1.add(btnOpenGameConfig);
		
		row1.add(btnClearLog);
		
		top.add(row1, BorderLayout.NORTH);
		
		lblOut.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		lblOut.setText("Destino hexid: " + GAME_HEX_PATH.getParent() + " | ");
		top.add(lblOut, BorderLayout.SOUTH);
		
		// ===== Center (table) =====
		table.setRowHeight(24);
		table.getTableHeader().setReorderingAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		// clique na tabela -> sincroniza combo
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				if (e.getValueIsAdjusting() || busy.get() || _syncingSelection)
					return;
				
				Integer id = getSelectedIdPreferTable();
				if (id != null)
				{
					_syncingSelection = true;
					try
					{
						cmbIds.setSelectedItem(id);
					}
					finally
					{
						_syncingSelection = false;
					}
				}
			}
		});
		
		// combo -> sincroniza tabela
		cmbIds.addActionListener(e -> {
			if (busy.get() || _syncingSelection)
				return;
			
			Integer id = getSelectedIdPreferCombo();
			if (id != null)
			{
				_syncingSelection = true;
				try
				{
					selectRowById(id);
				}
				finally
				{
					_syncingSelection = false;
				}
			}
		});
		
		JScrollPane center = new JScrollPane(table);
		center.setBorder(BorderFactory.createTitledBorder("Servers"));
		
		// ===== Bottom (log) =====
		logArea.setEditable(false);
		logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
		JScrollPane bottom = new JScrollPane(logArea);
		bottom.setBorder(BorderFactory.createTitledBorder("Log"));
		bottom.setPreferredSize(new Dimension(10, 240));
		
		// ===== Layout =====
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(center, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);
	}
	
	private void refreshTable()
	{
		if (busy.get())
			return;
		
		setBusy(true);
		appendInfo("Atualizando lista...");
		
		worker = new SwingWorker<>()
		{
			private Vector<Object[]> rows;
			private List<Integer> idsSorted;
			private Integer preferredSelectId;
			
			@Override
			protected Void doInBackground()
			{
				try
				{
					publish("Carregando servername.xml / registrados...");
					
					Map<Integer, String> names = GameServerTable.getInstance().getServerNames();
					Map<Integer, GameServerInfo> regs = GameServerTable.getInstance().getRegisteredGameServers();
					
					rows = new Vector<>();
					idsSorted = new ArrayList<>(names.keySet());
					idsSorted.sort(Comparator.naturalOrder());
					
					for (Integer id : idsSorted)
					{
						String name = names.get(id);
						boolean used = regs.containsKey(id);
						rows.add(new Object[]
						{
							id,
							name,
							used ? "USADO" : "LIVRE"
						});
					}
					
					// tenta preservar seleção atual (tabela > combo)
					preferredSelectId = getSelectedIdPreferTable();
					if (preferredSelectId == null)
						preferredSelectId = getSelectedIdPreferCombo();
					
					publish("OK. Total nomes: " + names.size() + " | Registrados: " + regs.size());
				}
				catch (Exception e)
				{
					publish("ERRO ao atualizar: " + e.getMessage());
				}
				return null;
			}
			
			@Override
			protected void process(List<String> chunks)
			{
				for (String s : chunks)
					appendInfo(s);
			}
			
			@Override
			protected void done()
			{
				try
				{
					if (rows != null && idsSorted != null)
					{
						_syncingSelection = true;
						try
						{
							model.setRowCount(0);
							for (Object[] r : rows)
								model.addRow(r);
							
							cmbIds.removeAllItems();
							for (Integer id : idsSorted)
								cmbIds.addItem(id);
							
							Integer selectId = preferredSelectId;
							if (selectId == null)
								selectId = findFirstFreeId();
							
							if (selectId != null)
							{
								cmbIds.setSelectedItem(selectId);
								selectRowById(selectId);
							}
							else if (model.getRowCount() > 0)
							{
								table.setRowSelectionInterval(0, 0);
							}
						}
						finally
						{
							_syncingSelection = false;
						}
					}
				}
				finally
				{
					setBusy(false);
				}
			}
		};
		
		worker.execute();
	}
	
	private void registerSelected()
	{
		Integer id = getSelectedIdPreferTable();
		if (id == null)
			id = getSelectedIdPreferCombo();
		
		if (id == null)
		{
			JOptionPane.showMessageDialog(this, "Selecione um ID (tabela ou combo).", "Aviso", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		Map<Integer, String> names = GameServerTable.getInstance().getServerNames();
		if (names.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Nenhum servername disponível.\nConfirme se servername.xml existe no LoginServer.", "Sem servername.xml", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		String name = names.get(id);
		if (name == null)
		{
			JOptionPane.showMessageDialog(this, "Não existe nome para o ID: " + id, "ID inválido", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if (GameServerTable.getInstance().getRegisteredGameServers().containsKey(id))
		{
			JOptionPane.showMessageDialog(this, "Esse ID já está em uso.", "Já usado", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		int opt = JOptionPane.showConfirmDialog(this, "Registrar o GameServer ID " + id + " (" + name + ")?\n\n" + "O sistema vai criar/atualizar automaticamente:\n" + "- " + GAME_HEX_PATH + "\n" + "", "Confirmar registro", JOptionPane.YES_NO_OPTION);
		
		if (opt != JOptionPane.YES_OPTION)
			return;
		
		if (busy.get())
			return;
		
		setBusy(true);
		
		final int chosenId = id;
		
		worker = new SwingWorker<>()
		{
			@Override
			protected Void doInBackground()
			{
				try
				{
					publish("Registrando ID " + chosenId + " (" + name + ")...");
					
					byte[] hexId = LoginServerThread.generateHex(16);
					
					// atualiza cache em memória + DB
					GameServerTable.getInstance().getRegisteredGameServers().put(chosenId, new GameServerInfo(chosenId, hexId));
					GameServerTable.getInstance().registerServerOnDB(hexId, chosenId, "");
					
					// compatível com seu padrão (pode sair com '-')
					String hexStr = new BigInteger(hexId).toString(16);
					
					writeHexidFile(GAME_HEX_PATH, hexStr, chosenId);
					publish("OK. hexid.txt atualizado em: " + GAME_HEX_PATH.toAbsolutePath());
					
					publish("Registro finalizado com sucesso.");
				}
				catch (Exception e)
				{
					publish("ERRO ao registrar: " + e.getMessage());
					e.printStackTrace();
				}
				
				return null;
			}
			
			@Override
			protected void process(List<String> chunks)
			{
				for (String s : chunks)
					appendInfo(s);
			}
			
			@Override
			protected void done()
			{
				try
				{
					// atualiza UI: marca como USADO e seleciona o próximo LIVRE
					SwingUtilities.invokeLater(() -> {
						setRowStatus(chosenId, true);
						
						Integer next = findNextFreeIdAfter(chosenId);
						if (next != null)
						{
							_syncingSelection = true;
							try
							{
								cmbIds.setSelectedItem(next);
								selectRowById(next);
							}
							finally
							{
								_syncingSelection = false;
							}
							appendInfo("Próximo ID livre selecionado automaticamente: " + next);
						}
						else
						{
							appendInfo("Aviso: não há mais IDs livres.");
						}
					});
				}
				finally
				{
					setBusy(false);
				}
			}
		};
		
		worker.execute();
	}
	
	private void cleanSelected()
	{
		Integer id = getSelectedIdPreferTable();
		if (id == null)
			id = getSelectedIdPreferCombo();
		
		if (id == null)
		{
			JOptionPane.showMessageDialog(this, "Selecione um ID na tabela ou no combo.", "Aviso", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		if (!GameServerTable.getInstance().getRegisteredGameServers().containsKey(id))
		{
			JOptionPane.showMessageDialog(this, "Esse ID não está registrado.", "Nada para limpar", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		int opt = JOptionPane.showConfirmDialog(this, "Remover o registro do GameServer ID " + id + "?\n\n" + "Isso remove do banco (gameservers).", "Confirmar limpeza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		
		if (opt != JOptionPane.YES_OPTION)
			return;
		
		if (busy.get())
			return;
		
		setBusy(true);
		
		final int chosenId = id;
		
		worker = new SwingWorker<>()
		{
			@Override
			protected Void doInBackground()
			{
				try
				{
					publish("Limpando ID " + chosenId + " no banco...");
					
					try (Connection con = ConnectionPool.getConnection(); PreparedStatement st = con.prepareStatement("DELETE FROM gameservers WHERE server_id=?"))
					{
						st.setInt(1, chosenId);
						st.executeUpdate();
					}
					
					GameServerTable.getInstance().getRegisteredGameServers().remove(chosenId);
					
					publish("OK. Gameserver #" + chosenId + " removido.");
				}
				catch (Exception e)
				{
					publish("ERRO ao limpar: " + e.getMessage());
					e.printStackTrace();
				}
				return null;
			}
			
			@Override
			protected void process(List<String> chunks)
			{
				for (String s : chunks)
					appendInfo(s);
			}
			
			@Override
			protected void done()
			{
				try
				{
					SwingUtilities.invokeLater(() -> {
						setRowStatus(chosenId, false);
						
						// após limpar, seleciona o próprio id (agora livre)
						_syncingSelection = true;
						try
						{
							cmbIds.setSelectedItem(chosenId);
							selectRowById(chosenId);
						}
						finally
						{
							_syncingSelection = false;
						}
						
						appendInfo("ID " + chosenId + " agora está LIVRE.");
					});
				}
				finally
				{
					setBusy(false);
				}
			}
		};
		
		worker.execute();
	}
	
	private void cleanAll()
	{
		if (GameServerTable.getInstance().getRegisteredGameServers().isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Não há registros para limpar.", "Info", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		int opt = JOptionPane.showConfirmDialog(this, "UNREGISTER ALL servers.\nTem certeza?", "Confirmar cleanall", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		
		if (opt != JOptionPane.YES_OPTION)
			return;
		
		if (busy.get())
			return;
		
		setBusy(true);
		
		worker = new SwingWorker<>()
		{
			@Override
			protected Void doInBackground()
			{
				try
				{
					publish("Limpando TODOS os registros no banco...");
					
					try (Connection con = ConnectionPool.getConnection(); PreparedStatement st = con.prepareStatement("DELETE FROM gameservers"))
					{
						st.executeUpdate();
					}
					
					GameServerTable.getInstance().getRegisteredGameServers().clear();
					
					publish("OK. Todos os gameservers foram removidos.");
				}
				catch (Exception e)
				{
					publish("ERRO ao limpar todos: " + e.getMessage());
					e.printStackTrace();
				}
				return null;
			}
			
			@Override
			protected void process(List<String> chunks)
			{
				for (String s : chunks)
					appendInfo(s);
			}
			
			@Override
			protected void done()
			{
				try
				{
					SwingUtilities.invokeLater(() -> {
						// atualiza toda a tabela rápido (status)
						for (int r = 0; r < model.getRowCount(); r++)
						{
							model.setValueAt("LIVRE", r, 2);
						}
						
						Integer firstFree = findFirstFreeId();
						if (firstFree != null)
						{
							_syncingSelection = true;
							try
							{
								cmbIds.setSelectedItem(firstFree);
								selectRowById(firstFree);
							}
							finally
							{
								_syncingSelection = false;
							}
						}
					});
				}
				finally
				{
					setBusy(false);
				}
			}
		};
		
		worker.execute();
	}
	
	private static void writeHexidFile(Path outFile, String hexStr, int serverId) throws Exception
	{
		Files.createDirectories(outFile.getParent());
		
		Properties p = new Properties();
		p.setProperty("HexID", hexStr);
		p.setProperty("ServerID", String.valueOf(serverId));
		
		try (OutputStream os = Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
		{
			// gera:
			// #the hexID to auth into login
			// #Sun Dec ...
			// HexID=...
			// ServerID=...
			p.store(os, "the hexID to auth into login");
		}
	}
	
	private Integer getSelectedIdPreferTable()
	{
		int row = table.getSelectedRow();
		if (row < 0)
			return null;
		
		Object v = model.getValueAt(row, 0);
		if (v instanceof Integer)
			return (Integer) v;
		
		if (v != null)
		{
			try
			{
				return Integer.parseInt(v.toString());
			}
			catch (Exception ignored)
			{
			}
		}
		return null;
	}
	
	private Integer getSelectedIdPreferCombo()
	{
		Object v = cmbIds.getSelectedItem();
		if (v instanceof Integer)
			return (Integer) v;
		
		if (v != null)
		{
			try
			{
				return Integer.parseInt(v.toString());
			}
			catch (Exception ignored)
			{
			}
		}
		return null;
	}
	
	private void setBusy(boolean on)
	{
		busy.set(on);
		
		cmbIds.setEnabled(!on);
		btnRefresh.setEnabled(!on);
		btnRegister.setEnabled(!on);
		btnClean.setEnabled(!on);
		btnCleanAll.setEnabled(!on);
		btnOpenGameConfig.setEnabled(!on);
		btnClearLog.setEnabled(!on);
		table.setEnabled(!on);
	}
	
	private void appendInfo(String msg)
	{
		logArea.append(msg + "\n");
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}
	
	private void selectRowById(int id)
	{
		for (int r = 0; r < model.getRowCount(); r++)
		{
			int rowId = Integer.parseInt(String.valueOf(model.getValueAt(r, 0)));
			if (rowId == id)
			{
				table.setRowSelectionInterval(r, r);
				table.scrollRectToVisible(table.getCellRect(r, 0, true));
				return;
			}
		}
	}
	
	private void setRowStatus(int id, boolean used)
	{
		for (int r = 0; r < model.getRowCount(); r++)
		{
			int rowId = Integer.parseInt(String.valueOf(model.getValueAt(r, 0)));
			if (rowId == id)
			{
				model.setValueAt(used ? "USADO" : "LIVRE", r, 2);
				return;
			}
		}
	}
	
	private Integer findFirstFreeId()
	{
		Map<Integer, GameServerInfo> regs = GameServerTable.getInstance().getRegisteredGameServers();
		for (int r = 0; r < model.getRowCount(); r++)
		{
			int id = Integer.parseInt(String.valueOf(model.getValueAt(r, 0)));
			if (!regs.containsKey(id))
				return id;
		}
		return null;
	}
	
	private Integer findNextFreeIdAfter(int currentId)
	{
		Map<Integer, GameServerInfo> regs = GameServerTable.getInstance().getRegisteredGameServers();
		
		// depois do atual
		for (int r = 0; r < model.getRowCount(); r++)
		{
			int id = Integer.parseInt(String.valueOf(model.getValueAt(r, 0)));
			if (id > currentId && !regs.containsKey(id))
				return id;
		}
		
		// fallback: primeiro livre
		return findFirstFreeId();
	}
	
	private static void openFolderSafe(Path folder)
	{
		try
		{
			if (folder != null)
			{
				Files.createDirectories(folder);
				if (Desktop.isDesktopSupported())
					Desktop.getDesktop().open(folder.toFile());
			}
		}
		catch (Exception ignored)
		{
		}
	}
}
