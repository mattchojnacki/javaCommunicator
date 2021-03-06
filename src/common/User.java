/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**
 *
 * @author mariusz
 */
public class User {
    
    public final int ID;
    private final String firstName;
    private final String lastName;
    private final String passwordHash; 
    public Integer isLogin; 
    
    public User(int ID, String firstName, String lastName, String passwordHash, int isLogin) {
        this.ID = ID;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.isLogin = isLogin;
    }
    public User(int ID, String firstName, String lastName, String password, String algName) {
        this.ID = ID;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = makeHash(password, algName);
        this.isLogin = 0;
    }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPasswordHash() { return passwordHash; }
    public Integer getIsLogin() { return isLogin; }
    public void setIsLogin(Integer isLogin){
        this.isLogin = isLogin;
    }
    
    
    public static String makeHash(String plain, String algName) {
        try {
            MessageDigest alg = java.security.MessageDigest.getInstance(algName);
            byte[] array = alg.digest(plain.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {}
        return null;
    }
    
    @Override
    public String toString() { return firstName + " " + lastName; }
}
