package org.wowtools.hppt.usb;

import org.usb4java.*;

import java.nio.ByteBuffer;

public class UsbHidCommunication {
    public static void main(String[] args) {
        // Vendor ID 和 Product ID (根据你的设备修改)
        final short VENDOR_ID = (short) 0x1a86; // 替换为实际的 Vendor ID
        final short PRODUCT_ID = (short) 0x7523; // 替换为实际的 Product ID

        // 初始化 USB 上下文
        Context context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("无法初始化 LibUsb", result);
        }

        try {
            // 查找目标设备
            Device device = findDevice(context, VENDOR_ID, PRODUCT_ID);
            if (device == null) {
                System.out.println("未找到目标设备");
                return;
            }

            // 打开设备
            DeviceHandle handle = new DeviceHandle();
            result = LibUsb.open(device, handle);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("无法打开设备", result);
            }

            try {
                // 声明接口编号 (通常为 0，对于 HID 设备)
                int interfaceNumber = 0;

                // 绑定设备接口
                result = LibUsb.claimInterface(handle, interfaceNumber);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("无法绑定接口", result);
                }

                try {
                    // 发送数据到设备 (假设使用端点 0x01，长度为 8 字节)
                    byte[] sendData = new byte[8];
                    sendData[0] = 0x01; // 示例数据
                    sendData[1] = 0x02; // 示例数据
                    ByteBuffer bb = ByteBuffer.wrap(sendData);
                    int transferred = LibUsb.controlTransfer(
                            handle,
                            (byte) (LibUsb.REQUEST_TYPE_CLASS | LibUsb.RECIPIENT_INTERFACE | LibUsb.ENDPOINT_OUT),
                            (byte) 0x09, // HID Set_Report 请求
                            (short) 0x0200, // Report Type 和 Report ID (根据你的设备修改)
                            (short) interfaceNumber, // 接口号
                            bb,
                            1000);
                    if (transferred < 0) {
                        throw new LibUsbException("发送数据失败", transferred);
                    }
                } finally {
                    // 释放设备接口
                    LibUsb.releaseInterface(handle, interfaceNumber);
                }
            } finally {
                // 关闭设备
                LibUsb.close(handle);
            }
        } finally {
            // 释放 USB 上下文
            LibUsb.exit(context);
        }
    }

    /**
     * 根据 Vendor ID 和 Product ID 查找设备
     */
    private static Device findDevice(Context context, short vendorId, short productId) {
        DeviceList deviceList = new DeviceList();
        int result = LibUsb.getDeviceList(context, deviceList);
        if (result < 0) {
            throw new LibUsbException("获取设备列表失败", result);
        }

        try {
            for (Device device : deviceList) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("获取设备描述失败", result);
                }
                if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId) {
                    return device;
                }
            }
        } finally {
            LibUsb.freeDeviceList(deviceList, true);
        }
        return null;
    }
}
