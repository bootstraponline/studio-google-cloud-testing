/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gct.testing.ui;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.*;
import com.android.ddmlib.log.LogReceiver;
import com.google.api.services.testing.model.AndroidDevice;
import com.google.api.services.testing.model.Device;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GhostCloudDevice implements IDevice {

  private final Device device;


  public GhostCloudDevice(Device device) {
    this.device = device;
  }

  @NonNull
  @Override
  public String getSerialNumber() {
    AndroidDevice androidDevice = device.getAndroidDevice();
    return (androidDevice.getAndroidModelId() + "-" + androidDevice.getAndroidVersionId() + "-" +
           androidDevice.getLocale() + "-" + androidDevice.getOrientation()).toLowerCase();
  }

  @Nullable
  @Override
  public String getAvdName() {
    return null;
  }

  @Override
  public DeviceState getState() {
    return DeviceState.OFFLINE;
  }

  @Override
  public Map<String, String> getProperties() {
    return null;
  }

  @Override
  public int getPropertyCount() {
    return 0;
  }

  @Nullable
  @Override
  public String getProperty(@NonNull String name) {
    if (name.equals(IDevice.PROP_BUILD_API_LEVEL)) {
      return device.getAndroidDevice().getAndroidVersionId();
    }
    return null;
  }

  @Override
  public boolean arePropertiesSet() {
    return false;
  }

  @Override
  public String getPropertySync(String name)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    return null;
  }

  @Override
  public String getPropertyCacheOrSync(String name)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    return null;
  }

  @Override
  public boolean supportsFeature(@NonNull Feature feature) {
    return true;
  }

  @Override
  public boolean supportsFeature(@NonNull HardwareFeature feature) {
    if (feature != IDevice.HardwareFeature.WATCH) {
      return true;
    }
    return false;
  }

  @Override
  public String getMountPoint(String name) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return false;
  }

  @Override
  public boolean isEmulator() {
    return false;
  }

  @Override
  public boolean isOffline() {
    return true;
  }

  @Override
  public boolean isBootLoader() {
    return false;
  }

  @Override
  public boolean hasClients() {
    return false;
  }

  @Override
  public Client[] getClients() {
    return new Client[0];
  }

  @Override
  public Client getClient(String applicationName) {
    return null;
  }

  @Override
  public SyncService getSyncService() throws TimeoutException, AdbCommandRejectedException, IOException {
    return null;
  }

  @Override
  public FileListingService getFileListingService() {
    return null;
  }

  @Override
  public RawImage getScreenshot() throws TimeoutException, AdbCommandRejectedException, IOException {
    return null;
  }

  @Override
  public RawImage getScreenshot(long timeout, TimeUnit unit) throws TimeoutException, AdbCommandRejectedException, IOException {
    return null;
  }

  @Override
  public void startScreenRecorder(@NonNull String remoteFilePath,
                                  @NonNull ScreenRecorderOptions options,
                                  @NonNull IShellOutputReceiver receiver)
    throws TimeoutException, AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {

  }

  @Override
  public void executeShellCommand(String command, IShellOutputReceiver receiver, int maxTimeToOutputResponse)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

  }

  @Override
  public void executeShellCommand(String command, IShellOutputReceiver receiver)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

  }

  @Override
  public void runEventLogService(LogReceiver receiver) throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public void runLogService(String logname, LogReceiver receiver) throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public void createForward(int localPort, int remotePort) throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public void createForward(int localPort, String remoteSocketName, DeviceUnixSocketNamespace namespace)
    throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public void removeForward(int localPort, int remotePort) throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public void removeForward(int localPort, String remoteSocketName, DeviceUnixSocketNamespace namespace)
    throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public String getClientName(int pid) {
    return null;
  }

  @Override
  public void pushFile(String local, String remote) throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {

  }

  @Override
  public void pullFile(String remote, String local) throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {

  }

  @Override
  public String installPackage(String packageFilePath, boolean reinstall, String... extraArgs) throws InstallException {
    return null;
  }

  @Override
  public void installPackages(List<String> apkFilePaths, int timeOutInMs, boolean reinstall, String... extraArgs) throws InstallException {

  }

  @Override
  public String syncPackageToDevice(String localFilePath) throws TimeoutException, AdbCommandRejectedException, IOException, SyncException {
    return null;
  }

  @Override
  public String installRemotePackage(String remoteFilePath, boolean reinstall, String... extraArgs) throws InstallException {
    return null;
  }

  @Override
  public void removeRemotePackage(String remoteFilePath) throws InstallException {

  }

  @Override
  public String uninstallPackage(String packageName) throws InstallException {
    return null;
  }

  @Override
  public void reboot(String into) throws TimeoutException, AdbCommandRejectedException, IOException {

  }

  @Override
  public Integer getBatteryLevel() throws TimeoutException, AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {
    return null;
  }

  @Override
  public Integer getBatteryLevel(long freshnessMs)
    throws TimeoutException, AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {
    return null;
  }

  @NonNull
  @Override
  public Future<Integer> getBattery() {
    return null;
  }

  @NonNull
  @Override
  public Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit) {
    return null;
  }

  @NonNull
  @Override
  public List<String> getAbis() {
    return null;
  }

  @Override
  public int getDensity() {
    return 0;
  }

  @Override
  public String getLanguage() {
    return null;
  }

  @Override
  public String getRegion() {
    return null;
  }

  @Override
  public String getName() {
    return "Cloud device: " + device.getId();
  }

  @Override
  public void executeShellCommand(String command, IShellOutputReceiver receiver, long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

  }

  @NonNull
  @Override
  public Future<String> getSystemProperty(@NonNull String name) {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GhostCloudDevice that = (GhostCloudDevice)o;

    if (device == null) {
      return that.device == null;
    }

    if (that.device == null) {
      return false;
    }

    return device.getId().equals(that.device.getId());
  }

  @Override
  public int hashCode() {
    return device != null ? device.getId().hashCode() : 0;
  }
}
