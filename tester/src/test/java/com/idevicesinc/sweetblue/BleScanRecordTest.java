package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.BleScanInfo;
import com.idevicesinc.sweetblue.utils.BleScanRecord;
import com.idevicesinc.sweetblue.utils.BleUuid;
import com.idevicesinc.sweetblue.utils.Utils_Byte;
import com.idevicesinc.sweetblue.utils.Utils_ScanRecord;
import com.idevicesinc.sweetblue.utils.Uuids;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.assertTrue;


public class BleScanRecordTest extends BaseTest
{


    @Test
    public void scanRecordTestWithServiceData() throws Exception
    {
        startTest(false);
        final UUID uuid = Uuids.BATTERY_SERVICE_UUID;
        final short manId = (short) 16454;
        final byte[] manData = new byte[] { 0x5,(byte) 0xAA, 0x44, (byte) 0xB3, 0x66 };
        BleScanRecord bleRecord = new BleScanRecord()
                .setName("Johnny 5")
                .setAdvFlags((byte) 1, (byte) 0x2)
                .setTxPower((byte) 10)
                .addServiceData(uuid, new byte[] { 100 })
                .addManufacturerData(manId, manData);
        byte[] record = bleRecord.buildPacket();
        BleScanRecord info = Utils_ScanRecord.parseScanRecord(record);
        assertTrue(info.getName().equals("Johnny 5"));
        assertTrue(info.getAdvFlags().value == 3);
        assertTrue(info.getManufacturerId() == manId);
        assertTrue(Arrays.equals(info.getManufacturerData(), manData));
        assertTrue(info.getTxPower().value == 10);
        Map<UUID, byte[]> services = info.getServiceData();
        assertTrue(services.size() == 1);
        assertTrue(Arrays.equals(services.get(uuid), new byte[] {100}));
        succeed();
    }

    @Test
    public void scanRecordTestWithShort() throws Exception
    {
        startTest(false);
        final UUID uuid = Uuids.BATTERY_SERVICE_UUID;
        final short manId = (short) 16454;
        final byte[] manData = new byte[] { 0x5,(byte) 0xAA, 0x44, (byte) 0xB3, 0x66 };
        BleScanRecord bleRecord = new BleScanRecord()
                .setName("Johnny 5")
                .setAdvFlags((byte) 0x3)
                .setTxPower((byte) 10)
                .addServiceUuid(uuid, BleUuid.UuidSize.SHORT)
                .setManufacturerId(manId)
                .setManufacturerData(manData);
        byte[] record = bleRecord.buildPacket();
        BleScanRecord info = Utils_ScanRecord.parseScanRecord(record);
        assertTrue(info.getName().equals("Johnny 5"));
        assertTrue(info.getAdvFlags().value == 3);
        assertTrue(info.getManufacturerId() == manId);
        assertTrue(Arrays.equals(info.getManufacturerData(), manData));
        assertTrue(info.getTxPower().value == 10);
        List<UUID> services = info.getServiceUUIDS();
        assertTrue(services.size() == 1);
        assertTrue(services.get(0).equals(uuid));
        succeed();
    }

    @Test
    public void scanRecordTestWithFull() throws Exception
    {
        startTest(false);
        final short manId = (short) 16454;
        final byte[] manData = new byte[] { 0x5,(byte) 0xAA, 0x44, (byte) 0xB3, 0x66 };
        BleScanRecord bleRecord = new BleScanRecord()
                .setName("Johnny 5")
                .setAdvFlags((byte) 1, (byte) 0x2)
                .setTxPower((byte) 10)
                .addServiceUuid(Uuids.BATTERY_SERVICE_UUID)
                .addServiceUuid(Uuids.DEVICE_INFORMATION_SERVICE_UUID)
                .setManufacturerId(manId)
                .setManufacturerData(manData);
        byte[] record = bleRecord.buildPacket();
        BleScanRecord info = Utils_ScanRecord.parseScanRecord(record);
        assertTrue(info.getName().equals("Johnny 5"));
        assertTrue(info.getAdvFlags().value == 3);
        assertTrue(info.getManufacturerId() == manId);
        assertTrue(Arrays.equals(info.getManufacturerData(), manData));
        assertTrue(info.getTxPower().value == 10);
        List<UUID> services = info.getServiceUUIDS();
        assertTrue(services.size() == 2);
        assertTrue(services.contains(Uuids.BATTERY_SERVICE_UUID));
        assertTrue(services.contains(Uuids.DEVICE_INFORMATION_SERVICE_UUID));
        succeed();
    }

    @Test
    public void scanRecordTestWithMedium() throws Exception
    {
        startTest(false);
        UUID myUuid = Uuids.fromInt("ABABCDCD");
        final short manId = (short) 16454;
        final byte[] manData = new byte[] { 0x5,(byte) 0xAA, 0x44, (byte) 0xB3, 0x66 };
        BleScanRecord bleRecord = new BleScanRecord()
                .setName("Johnny 5")
                .setAdvFlags((byte) 1, (byte) 0x2)
                .setTxPower((byte) 10)
                .addServiceUuid(Uuids.CURRENT_TIME_SERVICE, BleUuid.UuidSize.MEDIUM)
                .addServiceUuid(Uuids.CURRENT_TIME_SERVICE__CURRENT_TIME, BleUuid.UuidSize.MEDIUM)
                .addServiceUuid(myUuid, BleUuid.UuidSize.MEDIUM)
                .setManufacturerId(manId)
                .setManufacturerData(manData);
        byte[] record = bleRecord.buildPacket();
        BleScanRecord info = Utils_ScanRecord.parseScanRecord(record);
        assertTrue(info.getName().equals("Johnny 5"));
        assertTrue(info.getAdvFlags().value == 3);
        assertTrue(info.getManufacturerId() == manId);
        assertTrue(Arrays.equals(info.getManufacturerData(), manData));
        assertTrue(info.getTxPower().value == 10);
        List<UUID> services = info.getServiceUUIDS();
        assertTrue(services.size() == 3);
        assertTrue(services.contains(Uuids.CURRENT_TIME_SERVICE));
        assertTrue(services.contains(Uuids.CURRENT_TIME_SERVICE__CURRENT_TIME));
        assertTrue(services.contains(myUuid));
        succeed();
    }

    @Test
    public void multipleMfgDataTest() throws Exception
    {
        startTest(false);
        BleScanInfo info = new BleScanInfo();
        info.addManufacturerData((short) 14, new byte[] { 0x0, 0x1, 0x2 });
        info.addManufacturerData((short) 14, new byte[] { 0x3, 0x4, 0x5 });

        byte[] record = info.buildPacket();

        BleScanRecord info2 = Utils_ScanRecord.parseScanRecord(record);
        assertTrue(info2.getManufacturerDataList().size() == 2);
        succeed();
    }

    @Test
    public void serviceUUID128BitTest() throws Exception
    {
        startTest(false);
        // This is a sample raw scan record which contains a 128bit service uuid with data.
        byte[] rawRecord = Utils_Byte.hexStringToBytes("0201020709363534333231020AF11821024DE6A9087CC2831D48D87196E28455303132333435360000000000000000000000000000000000000000000000");
        BleScanRecord record = Utils_ScanRecord.parseScanRecord(rawRecord);
        assertTrue(record.getServiceData().size() > 0);
        byte[] data = null;
        for (UUID id : record.getServiceData().keySet())
        {
            data = record.getServiceData().get(id);
            break;
        }
        assertTrue(data != null && data.length > 0);
        succeed();
    }

}
