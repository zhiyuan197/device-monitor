package com.shenliwei.devicewatch.monitor;

import java.util.List;

public interface MonitorListener {
    default void onDeviceSnapshot(List<DeviceView> devices) {
    }

    default void onDeviceUpdate(DeviceView device) {
    }

    default void onAlertSnapshot(List<AlertView> alerts) {
    }

    default void onAlertUpdate(AlertView alert) {
    }

    default void onConnectionState(boolean connected, String detail) {
    }

    default void onRemoteError(String code, String message) {
    }

    default void onServiceCheckSnapshot(List<ServiceCheckView> checks) {
    }

    default void onServiceCheckUpdate(ServiceCheckView check) {
    }

    default void onServiceCheckDeleted(long checkId) {
    }
}

