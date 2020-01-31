package com.distribution.chat.client.src;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * A class represented a thread that listen to the server,
 * in order to release the ui main thread to interact with the user
 */
public class ClientListener extends Thread {

    public enum ACTION{
        REGISTER,
        LEAVE,
        DEFAULT
    }

    private Socket socket;
    public static boolean stop = false;
    public static ACTION action = ACTION.DEFAULT;
    /**
     * Constructor
     * @param socket
     */
    ClientListener(Socket socket) {
        this.socket = socket;
    }

    /**
     * A method that the thread will run when starts
     * This method listen for incoming messages from the server
     */
    @Override
    public void run() {
        String line = "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))){
            while((line != null) && (!line.trim().toUpperCase().equals("CLOSE")) && !stop)
            {
                if(!stop)
                {
                    line = br.readLine();
                }
                if((line != null) && (!line.trim().isEmpty()) && (!stop) && (!"CLOSE".equals(line)))
                {
                    if(line.startsWith("*topics"))
                    {
                        String[] topics = line.split("-");
                        if(!"empty".equals(topics[1]))
                        {
                            System.out.println("registered topics: " + topics[1]);
                        }
                    }
                    else {
                        switch (line.trim().toUpperCase()) {
                            case "OK":
                                if (ACTION.REGISTER.equals(action)) {
                                    System.out.println("OK - topic registered successfully");
                                } else if (ACTION.LEAVE.equals(action)) {
                                    System.out.println("OK - topic unregistered successfully");
                                } else {
                                    System.out.println("OK - Command executed successfully");
                                }
                                break;
                            case "ERROR":
                                if (ACTION.REGISTER.equals(action)) {
                                    System.out.println("ERROR - you tried to register topic twice");
                                } else if (ACTION.LEAVE.equals(action)) {
                                    System.out.println("ERROR - you tried to leave unregistered topic");
                                } else {
                                    System.out.println("ERROR - An error occurred while executing the command");
                                }
                                break;
                            default:
                                System.out.println(line);
                        }
                    }
                    action = ACTION.DEFAULT; //reset action
                    notifyClientUiTread(); //notify and wake up main UI thread
                }
            }
        }
        catch (SocketException e)
        {
            System.out.println("The server closed the connection");
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("Closing connection with " + socket.toString());
        }
        finally {
            Client.connected = false;
            try { socket.close(); } catch (IOException e) {
                System.out.println("Error trying to close the socket");
            }
            System.out.println("Connection closed with server " + socket);
            notifyClientUiTread();
        }
    }

    /**
     * A method that notify the UI thread of the client,
     * in order to receive reply from the server, before the UI main thread take control
     */
    private void notifyClientUiTread()
    {
        synchronized (Client.class) {
            Client.class.notify();
        }
    }
}
