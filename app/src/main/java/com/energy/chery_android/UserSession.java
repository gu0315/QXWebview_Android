package com.energy.chery_android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户会话：保存用户在主界面输入的信息。
 * H5 通过桥接（getUserInfo / app://login）读取这里的数据。
 */
public final class UserSession {

    public static final class Vehicle {
        public final String vin;
        public final String mac;

        public Vehicle(String vin, String mac) {
            this.vin = vin == null ? "" : vin;
            this.mac = mac == null ? "" : mac;
        }
    }

    private static final UserSession INSTANCE = new UserSession();

    private String phone = "";
    private String userId = "appUser";
    private String userName = "App用户";
    private boolean isLogin = true;
    private final List<Vehicle> vehicles = new ArrayList<>();

    private UserSession() {
    }

    public static UserSession getInstance() {
        return INSTANCE;
    }

    public void update(String phone, List<Vehicle> vehicles) {
        this.phone = phone == null ? "" : phone;
        this.vehicles.clear();
        if (vehicles != null) {
            this.vehicles.addAll(vehicles);
        }
        this.isLogin = true;
    }

    public String getPhone() {
        return phone;
    }

    /** H5 通过桥接获取的用户信息数据结构 */
    public Map<String, Object> userInfoPayload() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            Map<String, String> item = new HashMap<>();
            item.put("vin", vehicle.vin);
            item.put("mac", vehicle.mac);
            list.add(item);
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("phone", phone);
        userInfo.put("list", list);
        userInfo.put("userId", userId);
        userInfo.put("isLogin", isLogin);
        userInfo.put("userName", userName);
        return userInfo;
    }
}
