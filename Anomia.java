/*
Anomia
by Shalin Shah
*/

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.applet.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

import java.util.*;

public class Anomia 
{
	private static final char[] symbols = {'+', '=', '#', 'o', '-', '$', '%', '8'};
	private static final Color[] colors = {Color.GREEN, Color.MAGENTA, Color.BLUE, Color.ORANGE, Color.BLACK, Color.PINK, Color.RED, Color.CYAN};
	private static final int numPiles = 8;
	private static final int maxDeckSize = 200;
	private static final String[] originalDecks = {"Blue", "Red", "Purple", "Green", "Pink", "Yellow", "Brown", "Orange"};
	
	private static boolean MATCH_DETECTION_ON = false; //must be off for 2-3 player variant
	private static boolean COLORS_ON = true;
	private static String deckName = null; //default
	private static JButton selectedDeck;
	private static JTextField customDeckTextField = new JTextField (10);
	private static int numSymbols = symbols.length;
	
	private static String fileName = "";
	private static JLabel cardLoadError = new JLabel ("");
	
	private static final int outPortNum = 5000; //Can be changed to anything as long as portNum and portNum + 1 are valid, unused ports.
	private static final int inPortNum = outPortNum + 1;
	
	private static Border defaultButtonBorder = new JButton().getBorder();

	//Game state variables:
	private static enum GameType {SERVER, CLIENT, ONE_DEVICE};
	private static GameType gameType = null;

	private static enum GameState {SETUP, ONGOING, OVER}
	private static GameState gameState = GameState.SETUP;
	
	private static ObjectOutputStream out;
	private static ObjectInputStream in;
	
	private static JFrame gameTypeDeterminationWindow = new JFrame("Anomia");
	
	private static boolean inducedClick = false;
	private static boolean errorWindowOn = false;
	
	private static Stack<String> deck = new Stack<String>();
	private static PileButton[] piles = new PileButton[numPiles];
	private static final Font normalFont = new Font (Font.SANS_SERIF, Font.PLAIN, 24);
	private static final Font symbolFont = new Font (Font.SANS_SERIF, Font.PLAIN, 60);
	private static final Font minusNormalFont = new Font (Font.SANS_SERIF, Font.PLAIN, -24);
	
	private static int currentPile;
	private static PileButton[] currentMatch = null;
	private static JButton deckButton = new JButton();
	private static JButton selected = null;
	
	private static JFrame window = new JFrame ("Anomia");
	
	private static JFrame notificationWindow = new JFrame("Anomia");
	private static boolean notificationWindowInitialized = false;
	
	//Variables only used if Client:
	private static JFrame connectionSetup = new JFrame("Anomia");
	private static JLabel connectionSetupPrompt = new JLabel("Please enter IP address and port number of server (must be on same LAN).");
	private static JTextField IPAddressTextField = new JTextField(15);
	private static JTextField portTextField = new JTextField(6);
	private static JLabel connectionSetupError = new JLabel(" ");
	private static boolean connectionSetupInitialized = false;

	//GUI Objects:
	
	
	private static class PileButton extends JButton implements ActionListener
	{
		private Stack<String> pile = new Stack<String>();
		private int score = 0;
		private PileButton self = this;
		private int number;
		
		private JLabel top = new JLabel();
		private JLabel middle = new JLabel();
		private JLabel symbol = new JLabel();
		private JButton bottom = new JButton();
		private JLabel scoreLabel = new JLabel();
		

		public PileButton (int n)
		{
			number = n;
			this.setMargin(new Insets (0, 0, 0, 0));
			this.setBorder(defaultButtonBorder);
			this.addActionListener(this);
			this.setLayout(new GridLayout(3, 1));
			
			top.setFont(normalFont);
			symbol.setFont(symbolFont);
			bottom.setFont(minusNormalFont);
			
			middle.setLayout(new GridLayout (1, 3));
			
			symbol.setHorizontalAlignment(SwingConstants.CENTER);
			scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			scoreLabel.setFont(normalFont);
			
			middle.add(new JLabel());
			middle.add(symbol);
			middle.add(scoreLabel);
			
			bottom.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			bottom.setBorderPainted(false);
			bottom.setContentAreaFilled(false);
			bottom.setFocusPainted(false);
			bottom.setHorizontalAlignment (SwingConstants.RIGHT);
			bottom.addActionListener(new ActionListener()
			{
				public void actionPerformed (ActionEvent e)
				{
					self.doClick();
				}
			});
			
			this.add(bottom);
			this.add(middle);
			this.add(top);
			
			updateText();
			
			if (!COLORS_ON)
			{
				this.setBackground(Color.WHITE);
			}
		}
		
		public void updateText()
		{
			if (pile.isEmpty())
			{
				
				top.setText("(Empty)");
				symbol.setText("");
				bottom.setText("(Empty)");
				scoreLabel.setText("" + score);
				
				if (COLORS_ON)
				{
					this.setBackground (Color.LIGHT_GRAY);
					symbol.setForeground(Color.LIGHT_GRAY);
				}
				
				return;
			}
			
			top.setText(pile.peek());
			symbol.setText(Character.toString(getSymbol(pile.peek())));
			bottom.setText(pile.peek());
			if (COLORS_ON)
			{
				this.setBackground(Color.WHITE);
				symbol.setForeground(getColor(pile.peek()));
			}
			scoreLabel.setText("" + score);
		}
		
		public void score ()
		{
			score++;
			updateText();
		}

		public String getTopCard ()
		{
			if (pile.isEmpty())
			{
				return null;
			}
			return pile.peek();
		}
		
		public void removeTopCard ()
		{
			if (pile.isEmpty())
			{
				return;
			}
			
			pile.pop();
			updateText();
		}
		
		public void takeCardFromDeck()
		{
			Stack<String> temp = new Stack<String>();
			
			pile.push(deck.pop());
			updateText();

			deckButton.setText("Cards Left: " + deck.size());
		}
		
		public void actionPerformed (ActionEvent e)
		{
			if (gameType != GameType.ONE_DEVICE && !inducedClick)
			{
				writeInt (number);
			}
			
			if (MATCH_DETECTION_ON)
			{
				if (currentMatch != null)
				{
					if (this != currentMatch[0] && this != currentMatch[1])
					{
						return;
					}
					
					if (this == currentMatch[0])
					{
						currentMatch[1].score();
					}
					
					else
					{
						currentMatch[0].score();
					}
					
					removeTopCard();
					
					currentMatch = null;
				}
				
				else
				{
					if (deck.isEmpty())
					{
						return;
					}
					
					takeCardFromDeck();
				}
				
				if (pile.isEmpty())
				{
					return;
				}
		
				for (PileButton pb: piles)
				{
					if (pb == this)
					{
						continue;
					}
					if (pb.getTopCard() != null && getSymbol(pb.getTopCard()) == getSymbol(getTopCard()))
					{
						PileButton[] temp = {this, pb};
						currentMatch = temp;
					}
				}
			}
			
			else
			{
				if (selected == null)
				{
					if (!pile.isEmpty())
					{
						select(this);
					}
					
					return;
				}
				
				if (selected == deckButton)
				{
					if (deck.isEmpty())
					{
						return;
					}
					
					takeCardFromDeck();
					select (null);
					return;
				}
				
				PileButton pb = (PileButton) selected;
				
				if (selected == this)
				{
					select (null);
					return;
				}
				
				else
				{
					if (pb.getTopCard() == null)
					{
						select (this);
						return;
					}
					
					pb.removeTopCard();
					this.score();
					select (null);
				}
			}
		}
	}
	
	private static void select (JButton jb)
	{
		if (selected != null) //unselect on display
		{
			selected.setBorder(defaultButtonBorder);	
		}
		
		selected = jb;
		
		if (selected != null)
		{
			selected.setBorder(BorderFactory.createLineBorder(Color.RED, 3)); //select on display
		}
	}
	
	private static void selectDeck (JButton jb)
	{
		if (selectedDeck != null) //unselect on display
		{
			selectedDeck.setBorder(defaultButtonBorder);	
		}
		
		if (selectedDeck == jb)
		{
			selectedDeck = null;
			return;
		}
		
		selectedDeck = jb;
		
		if (selectedDeck != null)
		{
			selectedDeck.setBorder(BorderFactory.createLineBorder(Color.RED, 2)); //select on display
		}
	}
	
	private static class DeckChoiceButton extends JButton implements ActionListener
	{
		public DeckChoiceButton (String name)
		{
			super();
			this.setMargin(new Insets (0, 0, 0, 0));
			this.setFont(new Font (Font.SANS_SERIF, Font.BOLD, 12));
			this.setPreferredSize(new Dimension (80, 40));
			this.setText(name);
			addActionListener (this);
		}
		
		public void actionPerformed (ActionEvent e)
		{
			selectDeck (this);
		}
	}
	
	private static void getConfigSettings (File file)
	{
		try
		{
			Scanner input = new Scanner (new FileInputStream (file));
			int i = 0;
			while (input.hasNextLine())
			{
				String line = input.nextLine();
				if (line.indexOf("//") != -1)
				{
					line = line.substring(0, line.indexOf("//"));
				}
				line = line.trim();
				
				if (i == 0)
				{
					MATCH_DETECTION_ON = Boolean.parseBoolean(line);
				}
				
				if (i == 1)
				{
					COLORS_ON = Boolean.parseBoolean(line);
				}
				
				if (i == 2)
				{
					numSymbols = Integer.parseInt(line);
					if (numSymbols < 1 || numSymbols > symbols.length)
					{
						numSymbols = symbols.length;
					}
				}
				
				i++;
			}
			
			input.close();
		}
		
		catch (FileNotFoundException fe)
		{
			System.out.println ("Settings file not found. Proceeding using default settings.");
			return;
		}
		
		catch (Exception e)
		{
			System.out.println ("Unknown error in reading settings file. Proceeding using default settings.");
			//e.printStackTrace();
			return;
		}
	}
		
	public static boolean loadCards (File file)
	{
		//acceptable dictionary file is correct words separated by new lines. Give null file for client.
		if (gameType == GameType.CLIENT)
		{
			try
			{
				deck = (Stack<String>) in.readObject();
			}
			catch (Exception e)
			{
				exitProgram (e);
			}
			
			return true;
		}
		
		ArrayList<String> words = new ArrayList<String>();
		
		try
		{
			Scanner input = new Scanner (new FileInputStream (file));
			
			while (input.hasNextLine())
			{
				words.add (input.nextLine());	
			}
			
			input.close();
		}
		/*
		catch (FileNotFoundException fe)
		{
			cardLoadError.setText("Error: File \"" + fileName + "\" not found.");
			//System.out.println ("Error: File \"" + fileName + "\" not found.");
			return false;
		}
		*/
		catch (Exception e)
		{
			cardLoadError.setText("Unknown error in reading file.");
			//System.out.println ("Unknown error in reading file.");
			//e.printStackTrace();
			return false;
		}
		
		while (words.size() > 0)
		{
			int index = (int) (Math.random() * words.size());
			deck.push(words.remove(index));
		}
		
		return true;
	}

	
	public static char getSymbol (String s)
	{
		return (symbols[Math.abs(s.hashCode() % numSymbols)]);
	}
	
	public static Color getColor (String s)
	{
		return (colors[Math.abs(s.hashCode() % numSymbols)]);
	}
	
	private static void writeInt(int i)
	{
		try
		{
			out.writeObject(i);
		}
		catch (Exception e)
		{
			exitProgram(e);
		}
	}
	
	private static int readInt ()
	{
		try
		{
			int toReturn = (Integer) in.readObject();
			return toReturn;
		}
		catch (Exception e)
		{
			exitProgram(e);
			return -1; //To keep the compiler happy
		}
	}
	
	private static void exitProgram (Exception e)
	{	
		if (errorWindowOn && gameState != GameState.OVER)
		{
			e.printStackTrace();
			
			JFrame errorWindow = new JFrame("Anomia");
			errorWindow.setSize(500, 200);
			errorWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			errorWindow.setResizable(false);
			
			JPanel errorWindowTop = new JPanel();
			errorWindowTop.setLayout(new FlowLayout());
			JPanel errorWindowBottom = new JPanel();
			errorWindowBottom.setLayout(new FlowLayout());
			
			JLabel errorMessage = new JLabel();
			
			if (e instanceof SocketException)
			{
				errorMessage.setText("Fatal Error: Connection to opponent broken. Exiting program.");
			}
			
			else
			{
				errorMessage.setText("Fatal Error. Exiting Program.");
			}
			
			JButton okButton = new JButton("OK");
			okButton.addActionListener (new ActionListener()
			{
				public void actionPerformed (ActionEvent ae)
				{
					System.exit(0);
				}
			});
			
			errorWindowTop.add(errorMessage);
			errorWindowBottom.add(okButton);
			
			errorWindow.add(errorWindowTop, BorderLayout.NORTH);
			errorWindow.add(errorWindowBottom, BorderLayout.SOUTH);
			
			if (window != null)
			{
				window.setVisible(false);
			}
			
			errorWindow.setVisible(true);
		}
		
		else
		{
			System.exit(0);
		}
	}
	
	private static void showGameTypeDetermination ()
	{
		gameTypeDeterminationWindow.setSize(720, 360);
		gameTypeDeterminationWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		gameTypeDeterminationWindow.setResizable(false);
		
		JLabel welcomeLabel = new JLabel("Welcome to Anomia.");
		welcomeLabel.setFont(new Font (Font.SANS_SERIF, Font.BOLD, 16));
		JPanel welcomePanel = new JPanel();
		welcomePanel.setLayout(new FlowLayout());
		welcomePanel.add(welcomeLabel);
		
		JPanel gameTypeButtons = new JPanel();
		gameTypeButtons.setLayout(new FlowLayout());
		
		JButton serverButton = new JButton("Play as Server");
		serverButton.addActionListener(new ActionListener()
		{
			public void actionPerformed (ActionEvent e)
			{
				gameType = GameType.SERVER;
				initializeConnection();
			}
		});
		
		JButton clientButton = new JButton("Play as Client");
		clientButton.addActionListener(new ActionListener()
		{
			public void actionPerformed (ActionEvent e)
			{
				gameType = GameType.CLIENT;
				gameTypeDeterminationWindow.setVisible(false);
				initializeConnection();
			}
		});
		
		JButton oneDeviceButton = new JButton("Play on One Device");
		oneDeviceButton.addActionListener(new ActionListener()
		{
			public void actionPerformed (ActionEvent e)
			{
				gameType = GameType.ONE_DEVICE;
				initializeConnection();
			}
		});
		
		Dimension gameTypeDimension = new Dimension (220, 64);
		serverButton.setPreferredSize(gameTypeDimension);
		clientButton.setPreferredSize(gameTypeDimension);
		oneDeviceButton.setPreferredSize(gameTypeDimension);
		
		gameTypeButtons.add(serverButton);
		gameTypeButtons.add(clientButton);
		gameTypeButtons.add(oneDeviceButton);
		
		JPanel decksPanel = new JPanel();
		decksPanel.setLayout(new GridLayout (4, 1));
		decksPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		
		JPanel decksPanelTop = new JPanel();
		decksPanelTop.setLayout (new FlowLayout());
		JPanel decksPanelMiddle = new JPanel();
		decksPanelMiddle.setLayout (new FlowLayout());
		JPanel decksPanelBottom = new JPanel();
		decksPanelBottom.setLayout (new FlowLayout());
		
		JLabel selectDeckLabel = new JLabel ("Select Deck:");
		selectDeckLabel.setFont(new Font (Font.SANS_SERIF, Font.BOLD, 14));
		decksPanelTop.add(selectDeckLabel);
		for (String s: originalDecks)
		{
			decksPanelMiddle.add(new DeckChoiceButton (s));
		}
		decksPanelBottom.add(new DeckChoiceButton ("Custom:"));
		decksPanelBottom.add(customDeckTextField);
		
		JPanel cardLoadErrorPanel = new JPanel();
		cardLoadErrorPanel.setLayout(new FlowLayout());
		cardLoadErrorPanel.add(cardLoadError);
		
		decksPanel.add(decksPanelTop);
		decksPanel.add(decksPanelMiddle);
		decksPanel.add(decksPanelBottom);
		decksPanel.add(cardLoadErrorPanel);
		
		gameTypeDeterminationWindow.add(welcomePanel, BorderLayout.NORTH);
		gameTypeDeterminationWindow.add(gameTypeButtons, BorderLayout.CENTER);
		gameTypeDeterminationWindow.add(decksPanel, BorderLayout.SOUTH);

		gameTypeDeterminationWindow.setVisible(true);
	}

	private static void initializeConnection ()
	{	
		if (gameType != GameType.CLIENT)
		{
			if (selectedDeck == null)
			{
				cardLoadError.setText("Please specify which deck you would like to use.");
				return;
			}
			
			else if (selectedDeck.getText().contains("Custom"))
			{
				if (customDeckTextField.getText().trim().equals(""))
				{
					cardLoadError.setText("Please specify which deck you would like to use.");
					return;
				}
				
				deckName = customDeckTextField.getText();
			}
			
			else
			{
				deckName = selectedDeck.getText();
			}
			
			fileName = "Anomia_" + deckName + "_Deck.txt";
			boolean success = loadCards (new File(fileName));
			if (!success)
			{
				return;
			}
		
		}
		
		gameTypeDeterminationWindow.setVisible(false); //acceptable deck
		
		if (gameType == GameType.SERVER)
		{
			if (notificationWindowInitialized)
			{
				notificationWindow.setVisible(true);
				return;
			}
			
			String myAddress = "";
			try
			{
				myAddress = InetAddress.getLocalHost().getHostAddress();
			}
			catch (Exception e)
			{
				exitProgram(e);
			}
			
			notificationWindow.setSize(525, 150);
			notificationWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			notificationWindow.setResizable(false);
			notificationWindow.setLayout(new GridLayout(4,1));
			
			JLabel notification1 = new JLabel("Please inform client to enter connection information as follows.");
			JLabel notification2 = new JLabel("IP address: " + myAddress);
			JLabel notification3 = new JLabel("Port: " + outPortNum);
			JLabel notification4 = new JLabel("Game will start once client has connected.");
			
			notificationWindow.add(notification1);
			notificationWindow.add(notification2);
			notificationWindow.add(notification3);
			notificationWindow.add(notification4);
			
			notificationWindow.setVisible(true);
			notificationWindowInitialized = true;
			
			new WaiterForClientConnection().start();	
		}
		
		else if (gameType == GameType.CLIENT)
		{
			JButton backButton = new JButton("Back");
			backButton.addActionListener(new ActionListener()
			{
				public void actionPerformed (ActionEvent e)
				{
					connectionSetup.setVisible(false);
					notificationWindow.setVisible(false);
					gameType = null;
					showGameTypeDetermination();
				}
			});
			
			if (connectionSetupInitialized)
			{
				connectionSetup.setVisible(true);
				return;
			}
			
			connectionSetup.setSize(600, 125);
			connectionSetup.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			connectionSetup.setResizable(false);
			
			JPanel connectionSetupInput = new JPanel();
			connectionSetupInput.setLayout(new FlowLayout());
			
			JButton connectionSetupDoneButton = new JButton("Done");
			connectionSetupDoneButton.addActionListener(new ActionListener()
			{
				public void actionPerformed (ActionEvent e)
				{
					try
					{
						Socket socket1 = new Socket(IPAddressTextField.getText().trim(), Integer.parseInt(portTextField.getText().trim()));
						Socket socket2 = new Socket(IPAddressTextField.getText().trim(), Integer.parseInt(portTextField.getText().trim()) + 1);
						
						in = new ObjectInputStream(socket1.getInputStream());
						out = new ObjectOutputStream(socket2.getOutputStream());
					}
					
					catch (Exception exception)
					{
						connectionSetupError.setText("Error connecting to server. Please make sure the information you entered is correct and try again.");
						return;
					}
					
					connectionSetup.setVisible(false);
					initializeGame();
				}
			});
			
			connectionSetupInput.add(new JLabel("IP Address:"));
			connectionSetupInput.add(IPAddressTextField);
			connectionSetupInput.add(new JLabel("Port:"));
			connectionSetupInput.add(portTextField);
			connectionSetupInput.add(connectionSetupDoneButton);
			connectionSetupInput.add(backButton);
			
			connectionSetup.add(connectionSetupPrompt, BorderLayout.NORTH);
			connectionSetup.add(connectionSetupInput, BorderLayout.CENTER);
			connectionSetup.add(connectionSetupError, BorderLayout.SOUTH);
			
			connectionSetup.setVisible(true);
			connectionSetupInitialized = true;
		}
		
		else if (gameType == GameType.ONE_DEVICE)
		{
			initializeGame();
		}
	}

	private static class WaiterForClientConnection extends Thread
	{
		public void run ()
		{
			try
			{
				ServerSocket serverSocket = new ServerSocket(outPortNum);
				ServerSocket serverSocket2 = new ServerSocket(inPortNum);
				Socket socket1 = serverSocket.accept();
				Socket socket2 = serverSocket2.accept();
				
				out = new ObjectOutputStream(socket1.getOutputStream());
				in = new ObjectInputStream(socket2.getInputStream());
			}
			
			catch (Exception e)
			{
				exitProgram(e);
			}
			
			notificationWindow.setVisible(false);
			initializeGame();
		}
	}
	
	private static class Receiver extends Thread //Waits for the opponent to guess and then processes the opponent's guess.
	{
		public void run() 
		{
			try 
			{
				while (true)
				{
					int pileNumber = readInt();
					inducedClick = true;
					
					if (pileNumber == -1)
					{
						deckButton.doClick();
					}
					
					else
					{
						piles[pileNumber].doClick();
					}
					
					inducedClick = false;
				}
			}
			
			catch (Exception e)
			{
				exitProgram(e);
			}
		}
	}
	
	private static void initializeGame ()
	{	
		getConfigSettings (new File ("Anomia_settings.txt"));
		
		if (gameType == GameType.SERVER)
		{
			try
			{
				out.writeObject(deck);
			}
			
			catch (Exception e)
			{
				exitProgram (e);
			}
		}
		
		else if (gameType == GameType.CLIENT)
		{
			loadCards (new File (""));
		}
		
		if (COLORS_ON)
			deckButton.setBackground (Color.LIGHT_GRAY);
		else
			deckButton.setBackground (Color.WHITE);
		
		deckButton.addActionListener(new ActionListener()
		{
			public void actionPerformed (ActionEvent e)
			{
				if (gameType != GameType.ONE_DEVICE && !inducedClick)
				{
					writeInt (-1);
				}
				
				if (selected == deckButton)
				{
					select (null);
					deckButton.setText(deckButton.getText().substring(0, deckButton.getText().length() - 2));
					return;
				}
				
				select (deckButton);
				deckButton.setText(deckButton.getText() + " *");
			}
		});
		
		window.setSize(1150, 600);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setLayout (new GridLayout (3, 3));
		
		for (int i = 0; i < numPiles; i++)
		{
			piles[i] = new PileButton(i);
		}
		
		for (int i = 0; i < numPiles / 2; i++)
		{
			window.add(piles[i]);
		}
		
		window.add(deckButton);
		
		for (int i = 4; i < numPiles; i++)
		{
			window.add(piles[i]);
		}

		deckButton.setText("Cards Left: " + deck.size());
		
		if (gameType != GameType.ONE_DEVICE)
		{
			new Receiver().start();
		}
		
		window.setVisible(true);
	}
	
	
	public static void main (String[] args)
	{
		showGameTypeDetermination();
	}
}