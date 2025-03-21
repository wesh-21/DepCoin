package com.depchain.networking;

import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import com.depchain.utils.*;
interface MessageCallback {
    void onMessageReceived(AuthenticatedMessage authMessage);
}
public class AuthenticatedPerfectLinks implements MessageCallback {
    private List<AuthenticatedMessage> received;
    private StubbornLinks stubbornLink;
    private String destinationEntity; 
    private PublicKey endPointKey;
    private PrivateKey hostPrivateKey;
    
    /**
     * Constructor for AuthenticatedPerfectLinks.
     * 
     * @param destIP The destination IP address
     * @param destPort The destination port
     * @param hostPort The host port
     * @param destEntity The name of the destination entity (e.g., "member1", "leader")
     */
    public AuthenticatedPerfectLinks(String destinationIP, int destinationPort, int hostPort, String destinationEntity, PublicKey endPointKey, 
    PrivateKey hostPrivateKey) {
        try {
            this.destinationEntity = destinationEntity;
            this.stubbornLink = new StubbornLinks(destinationIP, destinationPort, hostPort, this);
            this.received = new ArrayList<>();
            this.endPointKey = endPointKey;
            this.hostPrivateKey = hostPrivateKey;
            Logger.log(Logger.AUTH_LINKS, "AuthenticatedPerfectLinks initialized for: " + destinationEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Clears all received messages from the buffer.
     */
    public void clearReceivedMessages() {
        synchronized (received) {
            received.clear();
        }
    }

    /**
     * Sends an authenticated message to the destination.
     * 
     * @param message The message to send
     * @throws Exception If encryption or sending fails
     */
    public void sendMessage(Message message) throws Exception {
        try {
            // Encrypt payload and command using RSA
            String encryptedPayload = Encryption.encryptWithRsa(message.getPayload(), this.endPointKey);
            String encryptedCommand = Encryption.encryptWithRsa(message.getCommand(), this.endPointKey);
            
            // Create message with encrypted content
            Message encryptedMessage = new Message(encryptedPayload, encryptedCommand);
            
            // Create authentication hash from the encrypted payload
            String authString = createHash(encryptedMessage.getPayload());
            
            // Create authenticated message for transmission
            AuthenticatedMessage authMessage = new AuthenticatedMessage(encryptedMessage, authString);
            Logger.log(Logger.AUTH_LINKS, "Sending authenticated message " + authMessage.getMessageID());
            
            // Send via stubborn link
            stubbornLink.sp2pSend(authMessage);
        } catch (Exception e) {
            Logger.log(Logger.AUTH_LINKS, "Error sending message: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Receives and verifies an authenticated message.
     * 
     * @param authMessage The authenticated message
     * @throws Exception If decryption fails
     */
    public void receiveMessage(AuthenticatedMessage authMessage) throws Exception {
        try {
            Logger.log(Logger.AUTH_LINKS, "Received authenticated message ID: " + authMessage.getMessageID());
            
            // First check if this is a duplicate message
            for (AuthenticatedMessage receivedMessage : received) {
                if (receivedMessage.getMessageID().equals(authMessage.getMessageID())) {
                    Logger.log(Logger.AUTH_LINKS, "Ignoring duplicate message: " + authMessage.getMessageID());
                    return;
                }
            }
            
            // Verify authentication hash
            if (verifyHash(authMessage)) {
                try {
                    // Decrypt payload and command
                    String decryptedPayload = Encryption.decryptWithRsa(authMessage.getPayload(), hostPrivateKey);
                    String decryptedCommand = Encryption.decryptWithRsa(authMessage.getCommand(), hostPrivateKey);
                    
                    // Create a new message with decrypted content
                    Message decryptedMessage = new Message(decryptedPayload, decryptedCommand);
                    
                    // Use original auth string from the encrypted message
                    AuthenticatedMessage processedMessage = new AuthenticatedMessage(decryptedMessage, authMessage.getAuthString());
                    
                    Logger.log(Logger.AUTH_LINKS, "Successfully decrypted message: " + processedMessage.getPayload() + " " + processedMessage.getCommand());
                    
                    // Add to received messages
                    received.add(processedMessage); // Store original message to prevent duplicates
                } catch (Exception e) {
                    Logger.log(Logger.AUTH_LINKS, "Decryption failed: " + e.getMessage());
                    throw e;
                }
            } else {
                Logger.log(Logger.AUTH_LINKS, "Failed to authenticate message: " + authMessage.getMessageID());
            }
        } catch (Exception e) {
            Logger.log(Logger.AUTH_LINKS, "Error processing message: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Authenticates a message by creating a hash of its content.
     * 
     * @param content The content to hash
     * @return The authentication string (SHA-256 hash)
     */
    public String createHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
   
    /**
     * Verifies the authentication of a message.
     * 
     * @param authMessage The authenticated message to verify
     * @return true if the message is authentic
     */
    public boolean verifyHash(AuthenticatedMessage authMessage) {
        String expectedAuthString = createHash(authMessage.getPayload());
        boolean hashMatches = authMessage.getAuthString().equals(expectedAuthString);
        
        if (!hashMatches) {
            Logger.log(Logger.AUTH_LINKS, "Hash verification failed");
            Logger.log(Logger.AUTH_LINKS, "Expected: " + expectedAuthString);
            Logger.log(Logger.AUTH_LINKS, "Actual: " + authMessage.getAuthString());
        }
        
        return hashMatches;
    }
  
    /**
     * Gets the size of the received message queue.
     * 
     * @return The number of received messages
     */
    public int getReceivedSize() {
        return received.size();
    }
    
    /**
     * Gets the list of received messages.
     * 
     * @return The list of authenticated messages
     */
    public List<AuthenticatedMessage> getReceivedMessages() {
        return received;
    }
    
    /**
     * Called when a message is received from the stubborn link.
     * 
     * @param authMessage The authenticated message
     */
    @Override
    public void onMessageReceived(AuthenticatedMessage authMessage) {
        try {
            receiveMessage(authMessage);
        } catch (Exception e) {
            Logger.log(Logger.AUTH_LINKS, "Error in callback: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the destination entity name.
     * 
     * @return The destination entity name
     */
    public String getDestinationEntity() {
        return destinationEntity;
    }
}