package project.client;

import com.javarush.task.task30.task3008.Connection;
import com.javarush.task.task30.task3008.ConsoleHelper;
import com.javarush.task.task30.task3008.Message;
import com.javarush.task.task30.task3008.MessageType;

import java.io.IOException;
import java.net.Socket;

public class Client {
    protected Connection connection;
    private volatile boolean clientConnected = false;

    public class SocketThread extends Thread {
        protected void processIncomingMessage(String message) {
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutAddingNewUser(String userName) {
            ConsoleHelper.writeMessage(String.format("Участник с ником '%s' присоединился к чату", userName));
        }

        protected void informAboutDeletingNewUser(String userName) {
            ConsoleHelper.writeMessage(String.format("Участник с ником '%s' покинул чат", userName));
        }

        protected void notifyConnectionStatusChanged(boolean clientConnected) {
            Client.this.clientConnected = clientConnected;
            synchronized (Client.this) {
                Client.this.notify();
            }
        }

        protected void clientHandshake() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();

                if (message.getType() == MessageType.NAME_REQUEST) {
                    String name = getUserName();
                    connection.send(new Message(MessageType.USER_NAME, name));
                } else if (message.getType() == MessageType.NAME_ACCEPTED) { 
                    notifyConnectionStatusChanged(true);
                    return;
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        protected void clientMainLoop() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();

                if (message.getType() == MessageType.TEXT) {
                    processIncomingMessage(message.getData());
                } else if (MessageType.USER_ADDED == message.getType()) {
                    informAboutAddingNewUser(message.getData());
                } else if (MessageType.USER_REMOVED == message.getType()) {
                    informAboutDeletingNewUser(message.getData());
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        @Override
        public void run() {
            try {
                String serverAddress = getServerAddress();
                int serverPort = getServerPort();
                Socket socket = new Socket(serverAddress, serverPort);
                connection = new Connection(socket);
                clientHandshake();
                clientMainLoop();
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Произошла ошибка в работе клиента");
                notifyConnectionStatusChanged(false);
            } catch (ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Произошла ошибка в работе клиента");
                notifyConnectionStatusChanged(false);
            }
        }
    }


    public void run() {
        SocketThread socketThread = getSocketThread();
        socketThread.setDaemon(true);
        socketThread.start();

        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException e) {
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента");
            return;
        }

        if (clientConnected) {
            ConsoleHelper.writeMessage("Соединение установлено");
        } else {
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента");
        }

        while (clientConnected) {
            String message = ConsoleHelper.readString();
            if (message.equalsIgnoreCase("exit")) {
                break;
            }
            if (shouldSendTextFromConsole()) {
                sendTextMessage(message);
            }
        }
    }

    protected String getServerAddress() {
        String address;
        while (true) {
            ConsoleHelper.writeMessage("Введите адрес сервера:\n");
            address = ConsoleHelper.readString();
            if (address.trim().isEmpty()) {
                ConsoleHelper.writeMessage("Вы ввели некоректный адрес");
                continue;
            }
            return address;
        }
    }

    protected int getServerPort() {
        int port;
        while (true) {
            ConsoleHelper.writeMessage("Введите номер порта:\n");
            port = ConsoleHelper.readInt();
            if (port == 0) {
                ConsoleHelper.writeMessage("Вы ввели некоректный порт");
                continue;
            }
            return port;
        }
    }

    protected String getUserName() {
        String username;
        while (true) {
            ConsoleHelper.writeMessage("Введите имя пользователя:\n");
            username = ConsoleHelper.readString();
            if (username.trim().isEmpty()) {
                ConsoleHelper.writeMessage("Вы ввели некорректное имя пользователя");
                continue;
            }
            return username;
        }
    }

    protected boolean shouldSendTextFromConsole() {
        return true;
    }

    protected SocketThread getSocketThread() {
        return new SocketThread();
    }

    protected void sendTextMessage(String text) {
        try {
            connection.send(new Message(MessageType.TEXT, text));
        } catch (IOException e) {
            ConsoleHelper.writeMessage("Ну удалось отправить сообщение");
            clientConnected = false;
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }

}
