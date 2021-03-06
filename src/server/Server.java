/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import client.Client;
import common.Message;
import common.User;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.FileSystemException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;




/**
 *
 * @author Mariusz
 */
public class Server implements Runnable {

    public static final int UPLOADLIMIT = 1024*1024*1024;
    public static final int UPLOADBUFFERSIZE = 64*1024;

    private static int port;
    private static Set<Server> servers = new HashSet<>();
    private static JLabel nThreadsLabel;
    private static JLabel nRegisteredUsersLabel;
    private static Database db;
    private static int userUID = 0;
    private ObjectOutputStream outputStream = null;
    
    //jtable
        private static Vector<Vector> newVec = new Vector<Vector>();
    private static Vector<String> columnNames = new Vector<String>();
    //

    
    public static void main(String[] args) throws IOException, SQLException {
        
        ServerSocket ssock = null;
        String dbURL = null;
        Connection dbConn = null;
        boolean startClientOnBoundPort = false;
        try {
            Properties props = new Properties();
	    props.load(new FileInputStream("Server.properties"));

            startClientOnBoundPort = Boolean.parseBoolean(props.getProperty("startClientOnBoundPort", "false"));
	    port = Integer.parseInt(props.getProperty("port"));
            ssock = new ServerSocket(port);
        
            Class.forName(props.getProperty("dbDriver")).newInstance();
            dbURL = props.getProperty("dbURL");
            dbConn = DriverManager.getConnection(dbURL);
            boolean dbInit = Boolean.parseBoolean(props.getProperty("dbInit", "false"));
            String adminPassword = dbInit ? props.getProperty("adminPassword", "admin") : null;
            db = new Database(dbConn, adminPassword);

        } catch(IOException e) {
            if(startClientOnBoundPort) {
                Client.main(args);
            } else {
                JOptionPane.showMessageDialog(null, "While binding port " + port + "\n" + e);
                System.exit(1);
            }
	} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            //JOptionPane.showMessageDialog(null, "Using DB " + dbURL + " not possible\n" + e);
            e.printStackTrace();
            System.exit(2);
        }

        if(ssock == null) return;
        
        JFrame mainWindow = new JFrame("Communicator server on port " + port);
        mainWindow.setSize(300, 120);
	mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	JPanel interior = new JPanel();
        interior.setBorder (new EmptyBorder(10, 10, 10, 10));
	interior.setLayout(new GridLayout(2, 2));
        interior.add(new JLabel("Active threads", JLabel.LEFT));
        nThreadsLabel = new JLabel("0", JLabel.RIGHT);
        interior.add(nThreadsLabel);
        interior.add(new JLabel("Registered users", JLabel.LEFT));
        nRegisteredUsersLabel = new JLabel("", JLabel.RIGHT);
        interior.add(nRegisteredUsersLabel);
        Dimension dim = mainWindow.getToolkit().getScreenSize();
	Rectangle abounds = mainWindow.getBounds();
	mainWindow.setLocation((dim.width - abounds.width) / 2, (dim.height - abounds.height) / 2);
        mainWindow.add(interior);
        mainWindow.setVisible(true);

        
        
        
        for(;;) {
            Socket sock = ssock.accept();
            Server server = new Server(sock);
            new Thread(server).start();
            refreshView(true);
        }
    }
    private Socket sock;
    private PrintWriter out;
    private int login = 0;
    private int sendTo = 0;
    private static Set<User> friendsList;
    private static Set<Integer> friendsIdList;
    
    
    private Server(Socket sock) throws IOException {
        this.sock = sock;
    }

    @Override
    public void run() {
        
        servers.add(this);
        refreshView(false);
       
        try {
	out = new PrintWriter(sock.getOutputStream(), true);
	BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        out.println("Use /help for help");
        mainLoop:
        for(;;) {
            

            String s = null;
            try {
                s = in.readLine();
            } catch(SocketException e) {
                break;
            }
            if(s == null) break;
            /*
                interpretation of a command/data sent from clients
            */
            if(s.charAt(0) == '/') {
                StringTokenizer st = new StringTokenizer(s);
                String cmd = st.nextToken();
                switch(cmd) {
                    case "/login":
                        if(st.hasMoreTokens()) {
                            try {
                                int loginCandidate = Integer.parseInt(st.nextToken());
                                User u = db.getUser(loginCandidate);
                                String passwordHash = User.makeHash(st.hasMoreTokens() ? st.nextToken() : "", "MD5");
                                if(!u.getPasswordHash().equals(passwordHash)) {
                                    out.println("/err Login failed");
                                } else {
                                    login = loginCandidate;
                                    userUID = login;
                                    out.println("Welcome on the board, " + u);
                                    u.setIsLogin(1);
                                    db.updateUser(login, u);
                                    
                                    Server.sendUpdateToAllUsers("/updateStatusLogin " + u.ID + " " +u.isLogin);
                                }
                            } catch(NumberFormatException ex) {
                                out.println("/err Non-integer user id used");    
                            } catch(SQLException ex) {
                                out.println("/err No such user");
                            }
                        } else {
                            login = 0; sendTo = 0;
                            out.println("You are logged out");
                        }
                        break;
                    case "/to":
                        if(st.hasMoreTokens()) {
                            try {
                                int sendToCandidate = Integer.parseInt(st.nextToken());
                                User u = db.getUser(sendToCandidate);
                                sendTo = sendToCandidate;
                                out.println("You have set default recipient to " + u);                            
                            } catch(NumberFormatException ex) {
                                out.println("/err Non-integer user id used");
                            } catch(SQLException ex) {
                                out.println("/err No such user");
                            }
                        } else {
                            sendTo = 0;
                            out.println("Default recipient unset");
                        }
                        break;
                    case "/who":
                        for(Server server: servers) {
                            try {
                                out.print((server.login > 0 ? db.getUser(server.login) : "[not logged in]") + " ");
                            } catch (SQLException ex) {
                                out.print(s);
                            }
                            if(server == this) out.print("(me)");
                            out.println(" from " + sock.getRemoteSocketAddress());
                        }
                        break;
                    case "/whoami":
                        if(login > 0) {
                            try {
                                out.println(db.getUser(login) + "\nWriting to " + db.getUser(sendTo));
                            } catch (SQLException ex) {
                                out.println("/err " + Database.ERRMSG);
                            }
                        } else {
                            out.println("/err You are not logged in");
                        }
                        break;
                    case "/list":
                        String pattern = "%";
                        if(st.hasMoreTokens()) {
                            pattern = st.nextToken();
                        }

                        try {
                            Set<Integer> ids = db.getUserIds(pattern);
                            for(Integer id: ids) {
                                User u = db.getUser(id);
                                out.println(id + ": " +u);
                            }
                        } catch (SQLException ex) {
                            out.println("/err " + Database.ERRMSG);
                        }
                        
                    case "/addFriends":
                        if(login > 0) {
                                int first = Integer.parseInt(st.nextToken());
                            try {
                                 db.addFriendship(login, first);
                                 // out.println("Correct addition to friends :)");
                                  
                    friendsList = new HashSet<> ();
            
                      friendsIdList = db.getFriendIds(login);

                    for (Integer number : friendsIdList){
                        User newUser = db.getUser(number);
                        friendsList.add(newUser);
                    }
                    StringBuilder stringbuilder = new StringBuilder();
                    
                    for(User newUser : friendsList){
                            String fff = newUser.ID + ";" + newUser.getFirstName() + ";" + newUser.getLastName() + ";" + newUser.getIsLogin();
                            stringbuilder.append(fff);
                            stringbuilder.append("@");
                    }
                    String toSendValue = stringbuilder.toString();
                    toSendValue = toSendValue.substring(0, toSendValue.length());
                    
                    out.println("/friendsList " + toSendValue);
                    out.flush();
                                  
                                  
                            } catch (SQLException ex) {
                                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (IllegalArgumentException ex) {
                           Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else {
                            out.println("/err You are not logged in");
                        }
                        break;
                    case "/deleteFriendship":
                        if(login > 0) {
                                int first = Integer.parseInt(st.nextToken());
                            try {
                                if(db.isFriend(login, first)){
                                    db.deleteFriendship(login, first);
                                    out.println("It's over your Friendship");
                                }else{
                                    out.println("You and this dude are not friends, and u cant delete him.");
                                }
                            } catch (SQLException ex) {
                                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (IllegalArgumentException ex) {
                           Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else {
                            out.println("/err You are not logged in");
                        }
                        break;
                    case "/register":
                        try {
                            int id = db.addUser(new User(-1, st.nextToken(), st.nextToken(), st.nextToken(), "MD5"));
                            out.println("Successfully registered as " + id);
                        } catch(NoSuchElementException ex) {
                            out.println("/err Use /register firstName lastName password");
                        } catch (SQLException ex) {
                            out.println("/err " + Database.ERRMSG);
                        }
                        break;
                    case "/unregister":
                        if(login > 0) {
                            try {
                                db.deleteUser(login);
                                login = 0; sendTo = 0;
                            } catch(Exception ex) {
                                out.println("/err" + Database.ERRMSG);
                            }
                        } else {
                            out.println("/err You should log in first");
                        }
                        break;
                    case "/upload":
                        synchronized(sock) {
                            try {
                                int bytesToRead = Integer.parseInt(st.nextToken());
                                if(bytesToRead < 0 || bytesToRead > UPLOADLIMIT) throw new FileSystemException("File to upload too big");
                                UUID uuid = UUID.randomUUID();
                                out.println("/uploadready " + uuid);
                                File uploadedFileName = new File("files/" + uuid);
                                FileOutputStream fos = new FileOutputStream(uploadedFileName);
                                byte[] buffer = new byte[UPLOADBUFFERSIZE];
                                int n, bytesRead = 0;
                                BufferedInputStream bis = new BufferedInputStream(sock.getInputStream());
                                while(bytesRead < bytesToRead) {
                                    n = bytesToRead - bytesRead; if(n > buffer.length) n = buffer.length;
                                    n = bis.read(buffer, 0, n);
                                    if(n > 0) {
                                        fos.write(buffer, 0, n); fos.flush();
                                        bytesRead += n;
                                        out.println("/uploaded " + bytesRead + " " + bytesToRead);
                                    }
                                }
                                fos.close();
                                out.println("/uploadcomplete " + uuid + " " + bytesToRead);
                            } catch(FileSystemException ex) {
                                out.println("/err " + ex.getMessage());
                            } catch(NumberFormatException ex) {
                                out.println("/err No file size");
                            }
                        }
                        break;
                    case "/download":
                        synchronized(sock) {
                            if(!st.hasMoreTokens()) {
                                out.println("/err No file uuid");
                            } else {
                                String fileName = st.nextToken();
                                try {
                                    File file = new File("files/" + fileName);
                                    long fileSize = file.length();
                                    out.println("/downloadready " + fileSize);
                                    byte[] buffer = new byte[UPLOADBUFFERSIZE];
                                    try (FileInputStream fis = new FileInputStream(file)) {
                                        BufferedOutputStream bos = new BufferedOutputStream(sock.getOutputStream());
                                        long bytesToSend = fileSize;
                                        while(bytesToSend > 0) {
                                            long k = fis.read(buffer);
                                            if(k > bytesToSend) k = bytesToSend;
                                            bos.write(buffer, 0, (int) k); bos.flush();
                                            bytesToSend -= k;
                                        }
                                        fis.close();
                                    }
                                } catch(FileNotFoundException ex) {
                                    out.println("/err No file " + fileName + " to download");
                                } catch(IOException ex) {
                                    out.println("/err Error during download " + fileName + "(" + ex + ")");
                                }
                            }
                        }
                        break;
                    case "/help":
                        BufferedReader help = new BufferedReader(new FileReader("help.txt"));
                        String line;
                        while((line = help.readLine()) != null) {
                            out.println(line);
                        }
                        break;
                    case "/exit":
                        break mainLoop;
                    default:
                        out.println("/err Unknown command " + cmd);
                }
            } else {
                if(login > 0) {
                    if(sendTo > 0) {
                        try {
                            Message msg = new Message(new Timestamp(System.currentTimeMillis()), null, login, sendTo, s);
                            int msgId = db.saveMessage(msg);
                            int count = 0;
                            for(Server server: servers) {
                                if(sendTo == server.login) {
                                    synchronized(sock) { 
                                        server.out.println("/from " + login + "\n" + s);
                                    }
                                    count++;
                                    if(count == 1) {
                                        db.markMessageAsRead(msgId);
                                    }
                                }
                            }
                            out.println("Message has sent to " + count + " recipient(s)");
                        } catch(SQLException ex) {
                                out.println("/err" + Database.ERRMSG);                            
                        }
                    } else {
                        out.println("You should set default recipient");
                    }
                } else {
                    out.println("You have to log in first");
                }
            }
        }
        } catch(IOException e) {}
        servers.remove(this);
        try {
            sock.close();
        } catch(Exception e) {}
        refreshView(false);
    }
    
    private static void sendUpdateToAllUsers(String status) {
        for(Server serv : servers) {
            serv.out.println(status);
            serv.out.flush();
        }
               
    }
    
    private static void refreshView(boolean withDB) {
        if(servers != null) {
            nThreadsLabel.setText("" + servers.size());
        }
        if(withDB && db != null) {
            try {
                nRegisteredUsersLabel.setText("" + db.countUsers());
            } catch (SQLException ex) {
                System.out.println(ex);
                nRegisteredUsersLabel.setText("n/a");
            }
        }
    }
    
}
