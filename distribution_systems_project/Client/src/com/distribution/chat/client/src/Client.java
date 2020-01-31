package com.distribution.chat.client.src;

import java.io.*;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

/**
 * A class represent client
 */
public class Client {
    public static boolean connected = false;
    private static PrintWriter printWriter = null;

    /**
     * The main method that run the main ui thread
     */
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String command = "";
        Properties config = new Properties();//object to load the properties file

        //print main menu
        printMenu();

        while (!command.trim().toUpperCase().equals("QUIT")) {
            System.out.println("\n(1)Connect (2)Register (3)Leave (4)Send (5)Disconnect (6)Quit\nChoose an option:");
            command = sc.nextLine();
            switch (command.toUpperCase().trim()) {
                case "1":
                case "C":
                case "CONNECT":
                    connect(config);
                    break;
                case "5":
                case "D":
                case "DISCONNECT":
                    if (connected) {
                        stopConnection(printWriter);
                    } else {
                        System.out.println("You need to connect first");
                    }
                    break;
                case "6":
                case "Q":
                case "QUIT":
                    if (connected)//close connection first
                    {
                        stopConnection(printWriter);
                        //Close resources
                        try {
                            printWriter.close();
                            sc.close();
                        }
                        catch (Exception e) {
                            System.out.println("An error occurred while trying to close one of the resources");
                        }
                    }
                    System.out.println("Closing the app --- GoodBye!");
                    System.exit(0);
                    break;
                case "2":
                case "R":
                case "REGISTER":
                    if (connected) {
                        //get registered topics from server
                        printWriter.write("getRegisterTopics\n");
                        printWriter.flush();
                        waitForServerReply(0);
                        System.out.println("Please insert a topic");
                        System.out.println("topic : ");
                        String topic = "";
                        topic = isEmptyLoop(sc, topic, "topic cannot be empty");
                        printWriter.write("REGISTER " + topic + "\n");
                        printWriter.flush();
                        ClientListener.action = ClientListener.ACTION.REGISTER; //setup action
                        waitForServerReply(0);
                    } else {
                        System.out.println("You need to connect first");
                    }
                    break;
                case "3":
                case "L":
                case "LEAVE":
                    if (connected) {
                        //get registered topics from server
                        printWriter.write("getRegisterTopics\n");
                        printWriter.flush();
                        waitForServerReply(0);
                        System.out.println("Please insert a topic");
                        System.out.println("topic : ");
                        String topic = "";
                        topic = isEmptyLoop(sc, topic, "topic cannot be empty");
                        printWriter.write("LEAVE " + topic + "\n");
                        printWriter.flush();
                        ClientListener.action = ClientListener.ACTION.LEAVE; //setup action
                        waitForServerReply(0);
                    } else {
                        System.out.println("You need to connect first");
                    }
                    break;
                case "4":
                case "S":
                case "SEND":
                    if (connected) {
                        System.out.println("Please insert a topic");
                        System.out.println("topic : ");
                        String topic = "";
                        topic = isEmptyLoop(sc, topic, "topic cannot be empty");
                        System.out.println("Please insert a sentence");
                        System.out.println("sentence : ");
                        String sentence = "";
                        sentence = isEmptyLoop(sc, sentence, "message cannot be empty");
                        printWriter.write("SEND " + topic + " " + sentence + "\n");
                        printWriter.flush();
                        waitForServerReply(100);
                    } else {
                        System.out.println("You need to connect first");
                    }
                    break;
                default:
                    System.out.println("Unsupported Command");
                    break;
            }
        }
    }

    /**
     * A method that reads the server ip and port from the configuration file
     * The method tries to connect to server
     * @param config - properties file object
     */
    private static void connect(Properties config) {
        if (!connected) {
            //Load the properties file
            try {
                config.load(new FileInputStream(System.getProperty("user.dir") + "/client-config.properties"));
            } catch (IOException e) {
                System.out.println("\nThe configuration file 'client-config.properties' was not found.");
                System.out.println("You need to configure this file, and locate it in the same location of the Client tool.");
                System.out.println("The requested location is: " + System.getProperty("user.dir"));
                return;
            }
            //Read Server information configuration from properties file
            String ip = config.getProperty("ip");
            int port;
            try {
                port = Integer.parseInt(config.getProperty("port"));
            } catch (NumberFormatException e) {
                System.out.println("\nPort must be a number!");
                System.out.println("Please modify the port in the configuration file, and try to connect again");
                return;
            }
            try {
                Socket socket = new Socket(ip, port);
                connected = true;
                printWriter = new PrintWriter(socket.getOutputStream());
                System.out.println("Connecting successfully to " + ip + " on port " + port);
                ClientListener.stop = false;
                ClientListener listener = new ClientListener(socket);
                listener.start();
            } catch (IOException e) {
                System.out.println("Can not connect to server - connection refused.");
                System.out.println("The server may be disconnected, if not check the configuration file.");
            }
        } else {
            System.out.println("You are already connected");
        }
    }

    /**
     * A method that print the client menu
     */
    private static void printMenu() {
        System.out.println("Welcome to client app\n" +
                "--- Client app menu ---\n" +
                "1. Connect (c) - Connect to the server listed in the configuration file\n" +
                "2. Register (r) - Register for a topic\n" +
                "3. Leave (l) - Leave a topic\n" +
                "4. Send (s) - Send a message\n" +
                "5. Disconnect (d) - Disconnect from the server\n" +
                "6. Quit (q) - Close client program");
    }

    /**
     * A method that stops the connection with the server
     * @param pw - the output stream to the sever
     */
    private static void stopConnection(PrintWriter pw) {
        connected = false;
        pw.write("CLOSE" + "\n");
        pw.flush();
        ClientListener.stop = true;
        waitForServerReply(0);
    }

    /**
     * A m method that run in a loop, as long as the input is empty
     * @param sc      - a scanner instance the connected to keyboard
     * @param input   - the input from the user
     * @param message - the message to be printed
     * @return - the none empty input from the user
     */
    private static String isEmptyLoop(Scanner sc, String input, String message) {
        while (isEmpty(input)) {
            input = sc.nextLine();
            if (isEmpty(input)) {
                System.out.println(message);
                System.out.println("please try again:");
            }
        }
        return input;
    }

    /**
     * A method that check if a string is empty
     * @param str - the string to be checked
     * @return true if the string is empty, false otherwise
     */
    private static boolean isEmpty(String str) {
        return (str == null) || (str.trim().isEmpty());
    }

    /**
     * A method the cause the main ui thread to wait, until the server thread will reply
     */
    private static void waitForServerReply(int timeout) {
        try {
            synchronized (Client.class) {
                Client.class.wait(timeout);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
