package org.wowtools.hppt.usb;

import org.usb4java.*;

import java.nio.ByteBuffer;

public class UsbExample {
    public static void main(String[] args) {
        // 初始化 USB 上下文
        Context context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("无法初始化 LibUsb", result);
        }

        // 获取设备列表
        DeviceList deviceList = new DeviceList();
        result = LibUsb.getDeviceList(context, deviceList);
        if (result < 0) {
            throw new LibUsbException("获取设备列表失败", result);
        }

        try {
            // 遍历设备列表
            for (Device device : deviceList) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("获取设备描述失败", result);
                }
                if (descriptor.idVendor() == 0X1A86) {
                    DeviceHandle handle = new DeviceHandle();
                    result = LibUsb.open(device, handle);
                    if (result != LibUsb.SUCCESS) {
                        new LibUsbException("Unable to open device", result).printStackTrace();
                        continue;
                    }
                    System.out.println("-----------------------------------");
                    System.out.printf("设备 %04x:%04x (供应商ID:产品ID)%n",
                            descriptor.idVendor(),
                            descriptor.idProduct());
// 假设设备需要 4 字节数据
                    ByteBuffer buffer = ByteBuffer.allocateDirect(4);
                    buffer.putInt(0, 0x12345678); // 填充数据

// 调用控制传输
                    result = LibUsb.controlTransfer(
                            handle,
                            (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.ENDPOINT_OUT), // 请求类型和方向
                            (byte) 0x01,       // 请求码
                            (short) 0,         // wValue
                            (short) 0,         // wIndex
                            buffer,            // 数据缓冲区
                            1000);             // 超时时间（毫秒）
                    if (result != LibUsb.SUCCESS) {
                        new LibUsbException("Unable to open write", result).printStackTrace();
                        continue;
                    }
                }


            }
        } finally {
            // 释放设备列表
            LibUsb.freeDeviceList(deviceList, true);
        }

        // 关闭 USB 上下文
        LibUsb.exit(context);
    }
}
