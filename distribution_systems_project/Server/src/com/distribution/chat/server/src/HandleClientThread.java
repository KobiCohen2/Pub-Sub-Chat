package com.distribution.chat.server.src;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

import static com.distribution.chat.server.src.Server.getCurrentDateTimeStamp;
import static com.distribution.chat.server.src.Server.writeToLog;

/**
 * A class represented a thread that listen to the client messages
 */
public class HandleClientThread extends Thread {

    public Socket clientSocket;
    private static Hashtable<Socket,Set<String>> clientTopicsTable = new Hashtable<>(); //table holds topics per client
    public boolean stop = false;

    /**
     * Constructor
     * @param socket
     */
    HandleClientThread(Socket socket)
    {
        this.clientSocket = socket;
        clientTopicsTable.put(clientSocket,new HashSet<>());
    }

    /**
     * A method that the thread will run when starts
     * This method listen for incoming messages from the client, doing process and reply to the user
     */
    @Override
    public void run()
    {
        System.out.println("[" + getCurrentDateTimeStamp() + "]" + " Received connection from: " + clientSocket);
        writeToLog(Server.LOG_LEVEL.INFO.toString(), "Received connection from: " + clientSocket);
            try (PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                 BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String line = "";

                while ((line != null) && (!line.trim().toUpperCase().equals("CLOSE")) && (!stop)) {
                    if (!stop && !clientSocket.isClosed()) {
                        synchronized (this) {
                            line = br.readLine();
                        }
                    }

                    if ((line != null) && (!line.trim().isEmpty()) && (!stop)) {
                        if("getRegisterTopics".equalsIgnoreCase(line))
                        {
                            sendRegisterTopics(pw);
                        }
                        else
                        {
                            line = processMessage(clientSocket, pw, line);
                        }
                    }
                }
            } catch (SocketException e1)
            {
                writeToLog(Server.LOG_LEVEL.FATAL.toString(), "the connection was interrupted by the client - " + clientSocket);
                writeToLog(Server.LOG_LEVEL.FATAL.toString(), getStackTraceAsString(e1));
                System.out.println("[" + Server.getCurrentDateTimeStamp() + "] the connection was interrupted by the client - " + clientSocket);
            } catch (IOException e)
            {
                writeToLog(Server.LOG_LEVEL.FATAL.toString(), "Error while try to read from client " + clientSocket);
                writeToLog(Server.LOG_LEVEL.FATAL.toString(), getStackTraceAsString(e));
                System.out.println("Error while try to read from client " + clientSocket);
            } finally {
                //remove from topic table
                synchronized (Server.class) {
                    if (clientTopicsTable.containsKey(clientSocket)) {
                        clientTopicsTable.remove(clientSocket);
                    }
                    //remove from threads table
                    if (Server.handleClients.contains(this)) {
                        Server.handleClients.remove(this);
                    }
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    writeToLog(Server.LOG_LEVEL.FATAL.toString(), "Error while try to close socket clientSocket: " + clientSocket);
                    writeToLog(Server.LOG_LEVEL.FATAL.toString(), getStackTraceAsString(e));
                    System.out.println("Error while try to close socket clientSocket: " + clientSocket);
                }
            }
    }

    /**
     * A method that process a raw message from the client
     * @param clientSocket - instance of the client socket connection
     * @param printWriter - instance of the print writer to the client
     * @param rawMessage - the raw message from the client
     * @return A string with the result of the process
     */
    private synchronized String processMessage(Socket clientSocket, PrintWriter printWriter, String rawMessage)
    {
        Hashtable<Server.TokenType,String> parsedMessage = Server.parseRawMessage(rawMessage);
        String result = "OK";

        switch (parsedMessage.get(Server.TokenType.ACTION).toUpperCase())
        {
            case "REGISTER":
                String rTopic = parsedMessage.get(Server.TokenType.TOPIC);
                if(clientTopicsTable.get(clientSocket).add(rTopic))
                {
                    sendToClient(printWriter, "OK");
                    //print on server
                    System.out.println("[" + Server.getCurrentDateTimeStamp() + "] Client " + clientSocket + " REGISTER " + rTopic);
                    //write to log
                    writeToLog(Server.LOG_LEVEL.INFO.toString(), "Client " + clientSocket + " REGISTER " + rTopic);
                }
                else
                {
                    sendToClient(printWriter, "ERROR");
                    //print on server
                    System.out.println("[" + Server.getCurrentDateTimeStamp() + "] ERROR - Client " + clientSocket + " REGISTER to existing topic - " + rTopic);
                    //write to log
                    writeToLog(Server.LOG_LEVEL.ERROR.toString(), "Client " + clientSocket + " REGISTER to existing topic - " + rTopic);
                }
                break;
            case "LEAVE":
                String lTopic = parsedMessage.get(Server.TokenType.TOPIC);
                if(clientTopicsTable.get(clientSocket).remove(lTopic))
                {
                    sendToClient(printWriter, "OK");
                    //print on server
                    System.out.println("[" + Server.getCurrentDateTimeStamp() + "] Client " + clientSocket + " LEAVE " + lTopic);
                    //write to log
                    writeToLog(Server.LOG_LEVEL.INFO.toString(), "Client " + clientSocket + " LEAVE " + lTopic);
                }
                else
                {
                    sendToClient(printWriter, "ERROR");

                    //print on server
                    System.out.println("[" + Server.getCurrentDateTimeStamp() + "] ERROR - Client " + clientSocket + " LEAVE an unregistered topic - " + lTopic);
                    //write to log
                    writeToLog(Server.LOG_LEVEL.ERROR.toString(), "Client " + clientSocket + " LEAVE an unregistered topic - " + lTopic);
                }
                break;
            case "SEND":
                String topic = parsedMessage.get(Server.TokenType.TOPIC);
                String content = parsedMessage.get(Server.TokenType.CONTENT);

                //print on server
                System.out.println("[" + Server.getCurrentDateTimeStamp() + "] Client " + clientSocket + " sent " + content + " on topic " + topic);
                //write to log
                writeToLog(Server.LOG_LEVEL.INFO.toString(), "Client " + clientSocket + " sent " + content + " on topic " + topic);
                String ipPort = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                //Send the message to the subscribers
                    clientTopicsTable.forEach((client, topics) ->
                    {
                        if (topics.contains(topic)) {
                            try {
                                PrintWriter pw = new PrintWriter(client.getOutputStream());
                                String output = "( " + topic + " ) " + ipPort + " " + Server.getCurrentTimeStamp() + " - " + content;

                                //send to client
                                sendToClient(pw, output);

                                //print on server
                                System.out.println("[" + Server.getCurrentDateTimeStamp() + "] FORWARD ( " + topic + " ) " + ipPort + " " + Server.getCurrentTimeStamp() + " - " + content + " to client - " + client);
                                //write to log
                                writeToLog(Server.LOG_LEVEL.INFO.toString(), "FORWARD ( " + topic + " ) " + ipPort + " " + Server.getCurrentTimeStamp() + " - " + content + " to client - " + client);
                            } catch (IOException e) {
                                System.out.println("Error occurred while sending message to client " + client);
                            }
                        }
                    });
                break;
            case "CLOSE":
                System.out.println("[" + Server.getCurrentDateTimeStamp() + "] CLOSE connection with " + clientSocket);
                //write to log
                writeToLog(Server.LOG_LEVEL.INFO.toString(), "CLOSE connection with " + clientSocket);
                this.stop = true;
                result = "CLOSE";
                break;
        }
        return result;
    }

    /**
     * A method that send a string to the client
     * @param pw - instance of the print writer to the client
     * @param message - the message to be sent to the client
     */
    private synchronized void sendToClient(PrintWriter pw, String message)
    {
            pw.write(message + "\n");
            pw.flush();
    }

    /**
     * A method that convert the printStackTrace output to string
     * @param ex - the thrown exception
     * @return A string of the printStackTrace output
     */
    public static String getStackTraceAsString(Exception ex)
    {
        StringWriter errors = new StringWriter();
        ex.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }

    /**
     * A method the send to the client his registered topics (if exists)
     * @param pw - instance of the print writer to the client
     */
    private void sendRegisterTopics(PrintWriter pw)
    {
        if(clientTopicsTable.containsKey(clientSocket))
        {
            sendToClient(pw, "*topics-" + clientTopicsTable.get(clientSocket).toString());
        }
        else
        {
            sendToClient(pw,"*topics-empty");
        }
    }
}
