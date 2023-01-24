package com.examples.android.blescanlegatt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 * This class was deprecated in API level 30.
 * Use ListFragment or RecyclerView to implement your Activity instead.
 * [流用元ソース GitHub]
 *   $ git clone https://github.com/android/connectivity-samples.git
 *   1.アプリケーションベース
 *     BluetoothLeGatt/
 *       Application/src/main/java/com/example/android/bluetoothlegatt/
 *         BluetoothLeService.java: デバックログ追加
 *         DeviceControlActivity.java: デバックログ追加
 *         DeviceScanActivity.java: 改造 (処理追加: No.2, No.3)
 *         SampleGattAttributes.java: 機能追加
 *   No.2 実行時権限許可可否処理
 *     NearbyConnectionsWalkieTalkie/
 *       app/src/main/java/com/google/location/nearby/apps/walkietalkie/ConnectionsActivity.java
 *   No.3 最新のBLEスキャン処理
 *     BluetoothAdapter -> (変更) BluetoothLeScanner
 *     BluetoothAdvertisements/
 *       Application/src/main/java/com/example/android/bluetoothadvertisements/ScannerFragment.java
 *        SampleScanCallback
 */
public class DeviceScanActivity extends ListActivity {
    private static final String TAG = DeviceScanActivity.class.getSimpleName();

    // ビルドバージョンごとの実行時権限リスト
    private static final String[] REQUIRED_PERMISSIONS;
    // [流用元]
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API:31) : newer
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                    };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API:29)
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                    };
        } else {
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                    };
        }
    }

    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    // Target BLE device Address: M5Stamp pico
//    private static final String M5_ADDRESS = "4C:75:25:97:58:CA";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private SampleScanCallback mLeScanCallback;
    private boolean mScanning;
    private Handler mHandler;

    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Called when the user has accepted (or denied) our permission request. */
    @Override
    public void onRequestPermissionsResult(int resultCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (resultCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            int i = 0;
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Log.w(TAG, "Failed to request the permission: " + permissions[i]);
                    Toast.makeText(this,
                            R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                i++;
            }
            recreate();
        }
        super.onRequestPermissionsResult(resultCode, permissions, grantResults);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, getRequiredPermissions())) {
            requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        startScanning();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                startScanning();
                break;
            case R.id.menu_stop:
                stopScanning();
                break;
        }
        return true;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        final Intent intent = new Intent(this, DeviceControlActivity.class);
        // @RequiresLegacyBluetoothPermission
        // @RequiresBluetoothConnectPermission
        // @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        // BluetoothDevice#getName()
        @SuppressLint("MissingPermission") final String deviceName = device.getName();
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, deviceName);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            stopScanning();
            mScanning = false;
        }
        startActivity(intent);
    }

    private void startScanning() {
        Log.d(TAG, "Starting Scanning mLeScanCallback: " + mLeScanCallback);

        if (mLeScanCallback == null) {
            // Will stop the scanning after a set time.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    stopScanning();
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            // Kick off a new scan.
            mScanning = true;
            mLeScanCallback = new SampleScanCallback();
            // Call requires permission which may be rejected by user:
            // code should explicitly check to see if permission is available (with checkPermission)
            // or explicitly handle a potential SecurityException
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                // ユーザーが実行時パーミッションを許可しなかった場合はスキャンをパス
                return;
            }

            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mLeScanCallback);
            String toastText = getString(R.string.scan_start_toast) + " "
                    + TimeUnit.SECONDS.convert(SCAN_PERIOD, TimeUnit.MILLISECONDS) + " "
                    + "seconds.";
            Log.d(TAG, toastText);
            Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Already scanning");
            Toast.makeText(this, R.string.already_scanning, Toast.LENGTH_SHORT).show();
        }
        invalidateOptionsMenu();
    }

    private void stopScanning() {
        Log.d(TAG, "Stopping Scanning");

        // Stop the scan, wipe th callback
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mBluetoothLeScanner.stopScan(mLeScanCallback);
        mLeScanCallback = null;

        // Even if no new results, update 'last seen' times.
        mLeDeviceListAdapter.notifyDataSetChanged();
    }

    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter.Builder builder = new ScanFilter.Builder();
//        builder.setDeviceAddress(M5_ADDRESS);
        scanFilters.add(builder.build());
        return scanFilters;
    }

    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        return builder.build();
    }

    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "onScanResult.callbackType: " + callbackType);
            Log.d(TAG, "onScanResult.result: " + result);

            //TODO BLEデバイス名がnullならリストに追加しない: M5Stack側ではBLEデバイス名を設定している
            if (result.getScanRecord().getDeviceName() != null) {
                mLeDeviceListAdapter.addDevice(result.getDevice());
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            Log.d(TAG, "onBatchScanResults_size: " + results.size());
            for (ScanResult result: results) {
                //TODO Add filtered valid deviceName
                if (result.getScanRecord().getDeviceName() != null) {
                    mLeDeviceListAdapter.addDevice(result.getDevice());
                }
            }
            mLeDeviceListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "onScanFailed_errorCode: " + errorCode);
            Toast.makeText(DeviceScanActivity.this, "Scan failed with error: " + errorCode,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            @SuppressLint("MissingPermission") final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
