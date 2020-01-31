package com.distribution.chat.server.src;


import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.net.NetworkInterface.getNetworkInterfaces;

/**
 * A class represented Server
 */
public class Server {

    /**
     * A set that hold all  the handle clients threads
     */
    static Set<HandleClientThread> handleClients = new HashSet<>();
    private static File logger;
    private static boolean connected = false;
    private static ServerSocket serverSocket = null;
    private static ServerListener listener = null;
    private static InetAddress chosenIp = null;

    /**
     * An enum that contains message token types
     */
    public enum TokenType {
        ACTION,
        TOPIC,
        CONTENT,
        BAD_REQUEST
    }

    /**
     * An enum that contains log level types
     */
    public enum LOG_LEVEL {
        INFO,
        ERROR,
        FATAL
    }

    /**
     * The main method that run the main ui thread
     */
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String line = "";
        Properties config = new Properties();//object to load the properties file

        try {
            //Create or open logger file
            logger = new File("ChatLog.log");

            //show menu
            showMenu();

            while (!"STOP".equals(line.trim().toUpperCase())) {
                System.out.println("\n(1)Start (2)Stop (3)Quit\nChoose an option:");
                line = sc.nextLine().toUpperCase().trim();
                switch (line) {
                    case "1":
                    case "START":
                        startConnection(config);
                        break;
                    case "2":
                    case "STOP":
                        if (connected) {
                            stopConnections();
                        } else {
                            System.out.println("You are not connected yet");
                        }
                        break;
                    case "3":
                    case "QUIT":
                        if (connected)// close connections first
                        {
                            stopConnections();
                        }
                        System.out.println("Server app will close now, GoodBye!");
                        writeToLog(LOG_LEVEL.INFO.toString(), "Server app closed");// write to log
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Unsupported command, please try again");
                }
            }
        }
        catch (NullPointerException e) {
            System.out.println("Error - can not create log file");
        }
    }

    /**
     * A methods that retrieve all ip addresses from all network interfaces in the pc
     * @return A set with all the ip addresses in the pc
     */
    private static Set<InetAddress> getAllInetAddresses() {
        Set<InetAddress> addresses = new HashSet<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            Collections.list(networkInterfaces).forEach(networkInterface -> addresses.addAll(Collections.list(networkInterface.getInetAddresses())));

            try {
                addresses.add(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}));
            } catch (UnknownHostException e) {
                System.out.println("Could not add address");
            }
        } catch (SocketException e) {
            System.out.println("Error while get all InetAddresses");
        }
        return addresses;
    }

    /**
     * A method that print the main menu
     */
    private static void showMenu() {
        System.out.println("Welcome to Sever app\n" +
                "--- Server app menu ---\n" +
                "You can use the following commands:\n" +
                "1. Start - start listening\n" +
                "2. Stop - stop listening\n" +
                "3. Quit - quit the app");
    }

    /**
     * A method that reads the port from the configuration file,
     * asks the user for ip, and try to start connection
     * @param config - properties file object
     */
    private static void startConnection(Properties config) {
        if (!connected) {
            try {
                config.load(new FileInputStream(System.getProperty("user.dir") + "/server-config.properties"));
            } catch (IOException e) {
                System.out.println("\nThe configuration file 'server-config.properties' was not found.");
                System.out.println("You need to configure this file, and locate it in the same location of the Server tool.");
                System.out.println("The requested location is: " + System.getProperty("user.dir"));
                return;
            }
            int port;
            try {
                port = Integer.parseInt(config.getProperty("port"));

                if(isPortInUse(port))
                {
                    System.out.println("\nThe port " + port + " is already in use");
                    System.out.println("Please modify the port in the configuration file, and try to connect again");
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println("\nPort must be a number!");
                System.out.println("Please modify the port in the configuration file, and try to connect again");
                return;
            }

            while (!connected) {
                chosenIp = printInetAddresses();
                try {
                    serverSocket = new ServerSocket(port, 10, chosenIp);
                    connected = true;
                }
                catch (IOException e) {
                    System.out.println("Connection Error - can not listen on ip - " + chosenIp);
                }
            }
            listener = new ServerListener(serverSocket);
            listener.start();
            System.out.println("[" + getCurrentDateTimeStamp() + "] " + "Server start listening to " + chosenIp + " on port " + port);
            //write to log
            writeToLog(LOG_LEVEL.INFO.toString(), "Server start listening to " + chosenIp + " on port " + port);
        } else {
            System.out.println("You are already listening");
        }
    }

    /**
     * A method that print all the ip addresses,
     * and interact with the user to choose address to listen on
     * @return the ip address to listen on
     */
    private static InetAddress printInetAddresses() {
        int index = 1;
        int chosenIndex = 0;
        Scanner scanner = new Scanner(System.in);
        Set<InetAddress> addresses = getAllInetAddresses();
        System.out.println("\nPlease choose an ip address to listen from the list below");
        for (InetAddress address : addresses) {
            System.out.println("[" + index++ + "] " + address);
        }
        System.out.println("[" + index + "] Write an address");

        do {
            System.out.println("Please choose a number: ");
            try {
                chosenIndex = scanner.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Please enter a number!");
                scanner.next();
            }
        }
        while (chosenIndex < 1 || chosenIndex > index);

        if (chosenIndex == index) {
            String address;
            InetAddress ip = null;
            boolean found = false;
            scanner.nextLine();//clear scanner buff
            System.out.println("Please insert ip address to listen: ");
            while (!found) {
                try {
                    address = scanner.nextLine();
                    ip = InetAddress.getByName(address);
                    found = true;
                } catch (UnknownHostException e) {
                    System.out.println("Unknown host, please try again.");
                }
            }
            return ip;
        }

        return (InetAddress) addresses.toArray()[chosenIndex - 1];
    }

    /**
     * A method that parse a raw message from the client
     * @param message - raw message from the client
     * @return A table contains parsed message
     */
    static Hashtable<TokenType, String> parseRawMessage(String message) {
        Hashtable<TokenType, String> table = new Hashtable<>(5);
        String[] tokens = message.split(" ");

        switch (tokens[0].trim().toUpperCase()) {
            case "REGISTER":
            case "LEAVE":
                table.put(TokenType.ACTION, tokens[0]);
                table.put(TokenType.TOPIC, tokens[1]);
                break;
            case "SEND":
                table.put(TokenType.ACTION, tokens[0]);
                table.put(TokenType.TOPIC, tokens[1]);
                //concatenate content back
                StringBuffer content = new StringBuffer();
                for (int i = 2; i < tokens.length; i++) {
                    content.append(" ").append(tokens[i]);
                }
                table.put(TokenType.CONTENT, content.toString());
                break;
            case "CLOSE":
                table.put(TokenType.ACTION, tokens[0]);
                break;
            default:
                table.put(TokenType.BAD_REQUEST, "");
        }
        return table;
    }

    /**
     * A method that stop the connections with the clients
     */
    private synchronized static void stopConnections() {
        try {
            for (HandleClientThread hc : handleClients) {
                PrintWriter pw = new PrintWriter(hc.clientSocket.getOutputStream());
                pw.write("CLOSE\n");
                pw.flush();
                hc.stop = true;
                System.out.println("[" + getCurrentDateTimeStamp() + "] Connection closed with socket " + hc.clientSocket);
                writeToLog(LOG_LEVEL.INFO.toString(), "Connection closed with socket " + hc.clientSocket);// write to log
            }
            handleClients.clear();
            listener.stop = true;
            serverSocket.close();
            connected = false;
            System.out.println("[" + getCurrentDateTimeStamp() + "]" + "Server stop listening to ip " + serverSocket.getInetAddress() + " on port " + serverSocket.getLocalPort());
        } catch (IOException e) {
            writeToLog(LOG_LEVEL.FATAL.toString(), HandleClientThread.getStackTraceAsString(e));
        }
    }

    /**
     * A method that checks if port is in use
     * @param port - the given port to check
     * @return true if the port in use, false otherwise
     */
    private static boolean isPortInUse(int port)
    {
        try(ServerSocket serverSocket = new ServerSocket(port))
        {
            return false;
        }
        catch (IOException e) {
            //do nothing, the port in use
        }
        return true;
    }

    /**
     * A method that retrieve the current time
     * @return A string of the current time
     */
    static String getCurrentTimeStamp() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * A method that retrieve the current date and time
     * @return A string of the current date and time
     */
    static String getCurrentDateTimeStamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    /**
     * A method that write a line to the log file
     * @param TAG  - the level of the log
     * @param line - content to write to the log
     */
    static void writeToLog(String TAG, String line) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logger, true))) {
            bw.write("[" + getCurrentDateTimeStamp() + "] " + TAG + " - " + line + "\n");
            bw.newLine();
        } catch (IOException e) {
            System.out.println("Error while writing to log");
        }
    }
}
