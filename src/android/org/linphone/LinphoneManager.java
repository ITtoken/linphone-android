/*
LinphoneManager.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package org.linphone;

/*
LinphoneManager.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.activities.LinphoneActivity;
import org.linphone.assistant.AssistantActivity;
import org.linphone.call.CallManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Buffer;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListener;
import org.linphone.core.ChatRoom;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Core.AuthMethod;
import org.linphone.core.Core.EcCalibratorStatus;
import org.linphone.core.Core.GlobalState;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.Core.RegistrationState;
import org.linphone.core.Core.ConfiguringState;
import org.linphone.core.CoreException;
import org.linphone.core.Factory;
import org.linphone.core.CoreListener;
import org.linphone.core.Event;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.InfoMessage;
import org.linphone.core.PresenceActivity;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.OpenH264DownloadHelperListener;
import org.linphone.core.PayloadType;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.Reason;
import org.linphone.core.SubscriptionState;
//import org.linphone.core.TunnelConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.core.tools.H264Helper;
import org.linphone.core.tools.OpenH264DownloadHelper;
import org.linphone.receivers.BluetoothManager;
import org.linphone.receivers.DozeReceiver;
import org.linphone.receivers.HookReceiver;
import org.linphone.receivers.KeepAliveReceiver;
import org.linphone.receivers.NetworkManager;
import org.linphone.receivers.OutgoingCallReceiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

/**
 *
 * Manager of the low level LibLinphone stuff.<br />
 * Including:<ul>
 * <li>Starting C liblinphone</li>
 * <li>Reacting to C liblinphone state changes</li>
 * <li>Calling Linphone android service listener methods</li>
 * <li>Interacting from Android GUI/service with low level SIP stuff/</li>
 * </ul>
 *
 * Add Service Listener to react to Linphone state changes.
 *
 */
public class LinphoneManager implements CoreListener, ChatMessageListener, SensorEventListener, AccountCreatorListener {

	private static LinphoneManager instance;
	private Context mServiceContext;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private Resources mR;
	private LinphonePreferences mPrefs;
	private Core mLc;
	private OpenH264DownloadHelper mCodecDownloader;
	private OpenH264DownloadHelperListener mCodecListener;
	private String lastLcStatusMessage;
	private String basePath;
	private static boolean sExited;
	private boolean mAudioFocused;
	private boolean echoTesterIsRunning;
	private boolean dozeModeEnabled;
	private boolean callGsmON;
	private int mLastNetworkType=-1;
	private ConnectivityManager mConnectivityManager;
	private BroadcastReceiver mKeepAliveReceiver;
	private BroadcastReceiver mDozeReceiver;
	private BroadcastReceiver mHookReceiver;
	private BroadcastReceiver mCallReceiver;
	private BroadcastReceiver mNetworkReceiver;
	private IntentFilter mKeepAliveIntentFilter;
	private IntentFilter mDozeIntentFilter;
	private IntentFilter mHookIntentFilter;
	private IntentFilter mCallIntentFilter;
	private IntentFilter mNetworkIntentFilter;
	private Handler mHandler = new Handler();
	private WakeLock mProximityWakelock;
	private AccountCreator accountCreator;
	private static List<ChatMessage> mPendingChatFileMessage;
	private static ChatMessage mUploadPendingFileMessage;
	private boolean mAreDisplayAlertMessage = false;
	private SensorManager mSensorManager;
	private Sensor mProximity;
	private boolean mProximitySensingEnabled;
	private boolean handsetON = false;
	private Address mCurrentChatRoomAddress;

	public String wizardLoginViewDomain = null;

	private static List<ChatMessageListener> simpleListeners = new ArrayList<ChatMessageListener>();
	public static void addListener(ChatMessageListener listener) {
		if (!simpleListeners.contains(listener)) {
			simpleListeners.add(listener);
		}
	}
	public static void removeListener(ChatMessageListener listener) {
		simpleListeners.remove(listener);
	}

	protected LinphoneManager(final Context c) {
		sExited = false;
		echoTesterIsRunning = false;
		mServiceContext = c;
		basePath = c.getFilesDir().getAbsolutePath();
		mLPConfigXsd = basePath + "/lpconfig.xsd";
		mLinphoneFactoryConfigFile = basePath + "/linphonerc";
		mConfigFile = basePath + "/.linphonerc";
		mLinphoneRootCaFile = basePath + "/rootca.pem";
		mDynamicConfigFile = basePath + "/assistant_create.rc";
		mRingSoundFile = basePath + "/ringtone.mkv";
		mRingbackSoundFile = basePath + "/ringback.wav";
		mPauseSoundFile = basePath + "/hold.mkv";
		mChatDatabaseFile = basePath + "/linphone-history.db";
		mCallLogDatabaseFile = basePath + "/linphone-log-history.db";
		mFriendsDatabaseFile = basePath + "/linphone-friends.db";
		mErrorToneFile = basePath + "/error.wav";
		mUserCertificatePath = basePath;

		mPrefs = LinphonePreferences.instance();
		mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
		mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
		mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
		mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
		mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		mR = c.getResources();
		mPendingChatFileMessage = new ArrayList<ChatMessage>();
	}

	private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
	private static final int dbStep = 4;
	/** Called when the activity is first created. */
	private final String mLPConfigXsd;
	private final String mLinphoneFactoryConfigFile;
	private final String mLinphoneRootCaFile;
	private final String mDynamicConfigFile;
	public final String mConfigFile;
	private final String mRingSoundFile;
	private final String mRingbackSoundFile;
	private final String mPauseSoundFile;
	private final String mChatDatabaseFile;
	private final String mCallLogDatabaseFile;
	private final String mFriendsDatabaseFile;
	private final String mErrorToneFile;
	private final String mUserCertificatePath;
	private byte[] mUploadingImage;
	private Timer mTimer;

	private void routeAudioToSpeakerHelper(boolean speakerOn) {
		Log.w("Routing audio to " + (speakerOn ? "speaker" : "earpiece") + ", disabling bluetooth audio route");
		BluetoothManager.getInstance().disableBluetoothSCO();

		enableSpeaker(speakerOn);
	}

	public boolean isSpeakerEnabled() {
		return mAudioManager != null && mAudioManager.isSpeakerphoneOn();
	}

	public void enableSpeaker(boolean enable) {
		mAudioManager.setSpeakerphoneOn(enable);
	}

	public void initOpenH264DownloadHelper() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
			Log.i("Android >= 5.1 we disable the download of OpenH264");
			OpenH264DownloadHelper.setOpenH264DownloadEnabled(false);
			return;
		}

		mCodecDownloader = Factory.instance().createOpenH264DownloadHelper(getContext());
		mCodecListener = new OpenH264DownloadHelperListener() {
			ProgressDialog progress;
			int ctxt = 0;
			int box = 1;

			@Override
			public void OnProgress(final int current, final int max) {
				mHandler.post(new Runnable() {
						@Override
						public void run() {
						OpenH264DownloadHelper ohcodec = LinphoneManager.getInstance().getOpenH264DownloadHelper();
						if (progress == null) {
							progress = new ProgressDialog((Context) ohcodec.getUserData(ctxt));
							progress.setCanceledOnTouchOutside(false);
							progress.setCancelable(false);
							progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
						} else if (current <= max) {
							progress.setMessage(getString(R.string.assistant_openh264_downloading));
							progress.setMax(max);
							progress.setProgress(current);
							progress.show();
						} else {
							progress.dismiss();
							progress = null;
							LinphoneManager.getLc().reloadMsPlugins(LinphoneManager.this.getContext().getApplicationInfo().nativeLibraryDir);
							if (ohcodec.getUserDataSize() > box && ohcodec.getUserData(box) != null) {
								((CheckBoxPreference) ohcodec.getUserData(box)).setSummary(mCodecDownloader.getLicenseMessage());
								((CheckBoxPreference) ohcodec.getUserData(box)).setTitle("OpenH264");
							}
						}
					}
					});
			}

			@Override
			public void OnError(final String error) {
				mHandler.post(new Runnable() {
						@Override
						public void run() {
						if (progress != null) progress.dismiss();
						AlertDialog.Builder builder = new AlertDialog.Builder((Context) LinphoneManager.getInstance().getOpenH264DownloadHelper().getUserData(ctxt));
						builder.setMessage(getString(R.string.assistant_openh264_error));
						builder.setCancelable(false);
						builder.setNeutralButton(getString(R.string.ok), null);
						builder.show();
					}
					});
			}
		};
		mCodecDownloader.setOpenH264HelperListener(mCodecListener);
	}

	public OpenH264DownloadHelperListener getOpenH264HelperListener() {
		return mCodecListener;
	}

	public OpenH264DownloadHelper getOpenH264DownloadHelper(){
		return mCodecDownloader;
	}

	public void routeAudioToSpeaker() {
		routeAudioToSpeakerHelper(true);
	}

	public String getUserAgent() {
		try {
			StringBuilder userAgent = new StringBuilder();
			userAgent.append("LinphoneAndroid/" + mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(),0).versionCode);
			userAgent.append(" (");
			userAgent.append("Linphone/" + LinphoneManager.getLc().getVersion() + "; ");
			userAgent.append(Build.DEVICE + " " + Build.MODEL + " Android/" + Build.VERSION.SDK_INT);
			userAgent.append(")");
			return userAgent.toString();
		} catch (NameNotFoundException nnfe) {
			Log.e(nnfe);
		}
		return null;
	}

	public void routeAudioToReceiver() {
		routeAudioToSpeakerHelper(false);
	}

	public synchronized static final LinphoneManager createAndStart(Context c) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already initialized");

		instance = new LinphoneManager(c);
		instance.startLibLinphone(c);
		instance.initOpenH264DownloadHelper();

		// H264 codec Management - set to auto mode -> MediaCodec >= android 5.0 >= OpenH264
		H264Helper.setH264Mode(H264Helper.MODE_AUTO, getLc());

		TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
		setGsmIdle(gsmIdle);

		return instance;
	}

	public void addDownloadMessagePending(ChatMessage message){
		synchronized (mPendingChatFileMessage) {
			mPendingChatFileMessage.add(message);
		}
	}

	public boolean isMessagePending(ChatMessage message){
		boolean messagePending = false;
		synchronized (mPendingChatFileMessage) {
			for (ChatMessage chat : mPendingChatFileMessage) {
				if (chat.getStorageId() == message.getStorageId()) {
					messagePending = true;
					break;
				}
			}
		}
		return messagePending;
	}

	public void removePendingMessage(ChatMessage message){
		synchronized (mPendingChatFileMessage) {
			for (ChatMessage chat : mPendingChatFileMessage) {
				if (chat.getStorageId() == message.getStorageId()) {
					mPendingChatFileMessage.remove(chat);
				}
				break;
			}
		}
	}

	public void setUploadPendingFileMessage(ChatMessage message){
		mUploadPendingFileMessage = message;
	}

	public ChatMessage getMessageUploadPending(){
		return mUploadPendingFileMessage;
	}

	public void setUploadingImage(byte[] array){
		this.mUploadingImage = array;
	}

	@Override
	public void onMsgStateChanged(ChatMessage msg, ChatMessage.State state) {
		if (state == ChatMessage.State.FileTransferDone) {
			if (msg.isOutgoing() && mUploadingImage != null) {
				mUploadPendingFileMessage = null;
				mUploadingImage = null;
			} else {
				LinphoneUtils.storeImage(getContext(), msg);
				removePendingMessage(msg);
			}
		}

		if (state == ChatMessage.State.FileTransferError) {
			LinphoneUtils.displayErrorAlert(getString(R.string.image_transfert_error), LinphoneActivity.instance());
		}

		for (ChatMessageListener l: simpleListeners) {
			l.onMsgStateChanged(msg, state);
		}
	}

	@Override
	public void onFileTransferRecv(ChatMessage msg, Content content, Buffer buffer) {
	}

	@Override
	public Buffer onFileTransferSend(ChatMessage message, Content content, int offset, int size) {
		if (mUploadingImage != null && size > 0) {
			Buffer bufferToFill = Factory.instance().createBuffer();
			byte[] data = new byte[size];
			if (offset + size <= mUploadingImage.length) {
				for (int i = 0; i < size; i++) {
					data[i] = mUploadingImage[i + offset];
				}
				bufferToFill.setContent(data, size);
				bufferToFill.setSize(size);
				return bufferToFill;
			} else {
				Log.e("Error, upload task asking for more bytes( " + (size+offset) + " ) than available (" + mUploadingImage.length + ")");
			}
		}
		return null;
	}

	@Override
	public void onFileTransferProgressIndication(ChatMessage msg, Content content, int offset, int total) {
		for (ChatMessageListener l: simpleListeners) {
			l.onFileTransferProgressIndication(msg, content, offset, total);
		}
	}

	private boolean isPresenceModelActivitySet() {
		Core lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null) {
			return lc.getPresenceModel() != null && lc.getPresenceModel().getActivity() != null;
		}
		return false;
	}

	public void changeStatusToOnline() {
		Core lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null && isPresenceModelActivitySet() && lc.getPresenceModel().getActivity().getType() != PresenceActivity.Type.TV) {
			lc.getPresenceModel().getActivity().setType(PresenceActivity.Type.TV);
		} else if (isInstanciated() && lc != null && !isPresenceModelActivitySet()) {
			PresenceModel model = lc.createPresenceModelWithActivity(PresenceActivity.Type.TV, null);
			lc.setPresenceModel(model);
		}
	}

	public void changeStatusToOnThePhone() {
		Core lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && isPresenceModelActivitySet() && lc.getPresenceModel().getActivity().getType() != PresenceActivity.Type.OnThePhone) {
			lc.getPresenceModel().getActivity().setType(PresenceActivity.Type.OnThePhone);
		} else if (isInstanciated() && !isPresenceModelActivitySet()) {
			PresenceModel model = lc.createPresenceModelWithActivity(PresenceActivity.Type.OnThePhone, null);
			lc.setPresenceModel(model);
		}
	}

	public void changeStatusToOffline() {
		Core lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null) {
			lc.getPresenceModel().setBasicStatus(PresenceBasicStatus.Closed);
		}
	}

	public void subscribeFriendList(boolean enabled){
		Core lc = getLcIfManagerNotDestroyedOrNull();
		if(lc != null && lc.getFriendsLists() != null && lc.getFriendsLists().length > 0) {
			FriendList mFriendList = (lc.getFriendsLists())[0];
			Log.i("Presence list subscription is " + (enabled ? "enabled" : "disabled"));
			mFriendList.enableSubscriptions(enabled);
		}
	}


	public static synchronized final LinphoneManager getInstance() {
		if (instance != null) return instance;

		if (sExited) {
			throw new RuntimeException("Linphone Manager was already destroyed. "
					+ "Better use getLcIfManagerNotDestroyedOrNull and check returned value");
		}

		throw new RuntimeException("Linphone Manager should be created before accessed");
	}

	public static synchronized final Core getLc() {
		return getInstance().mLc;
	}

	public String getLPConfigXsdPath() {
		return mLPConfigXsd;
	}

	public void newOutgoingCall(AddressType address) {
		String to = address.getText().toString();
		newOutgoingCall(to, address.getDisplayedName());
	}

	public void newOutgoingCall(String to, String displayName) {
//		if (mLc.inCall()) {
//			listenerDispatcher.tryingNewOutgoingCallButAlreadyInCall();
//			return;
//		}
		if (to == null) return;

		// If to is only a username, try to find the contact to get an alias if existing
		if (!to.startsWith("sip:") || !to.contains("@")) {
			LinphoneContact contact = ContactsManager.getInstance().findContactFromPhoneNumber(to);
			if (contact != null) {
				String alias = contact.getPresenceModelForUriOrTel(to);
				if (alias != null) {
					to = alias;
				}
			}
		}

		ProxyConfig lpc = getLc().getDefaultProxyConfig();
		/*if (lpc != null) {
			to = lpc.normalizePhoneNumber(to);
		}*/

		Address lAddress;
		lAddress = mLc.interpretUrl(to);
		if (lAddress == null)  {
			return;
		}
		if (mR.getBoolean(R.bool.forbid_self_call) && lpc != null && lAddress.asStringUriOnly().equals(lpc.getIdentityAddress())) {
			return;
		}
		lAddress.setDisplayName(displayName);

		boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

		if (mLc.isNetworkReachable()) {
			try {
				if (Version.isVideoCapable()) {
					boolean prefVideoEnable = mPrefs.isVideoEnabled();
					boolean prefInitiateWithVideo = mPrefs.shouldInitiateVideoCall();
					CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo, isLowBandwidthConnection);
				} else {
					CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection);
				}


			} catch (CoreException e) {
				return;
			}
		} else if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		} else {
			Log.e("Error: " + getString(R.string.error_network_unreachable));
		}
	}

	private void resetCameraFromPreferences() {
		boolean useFrontCam = mPrefs.useFrontCam();
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == useFrontCam)
				camId = androidCamera.id;
		}
		String[] devices = getLc().getVideoDevicesList();
		String newDevice = devices[camId];
		LinphoneManager.getLc().setVideoDevice(newDevice);
	}

	public static interface AddressType {
		void setText(CharSequence s);
		CharSequence getText();
		void setDisplayedName(String s);
		String getDisplayedName();
	}


	public static interface NewOutgoingCallUiListener {
		public void onWrongDestinationAddress();
		public void onCannotGetCallParameters();
		public void onAlreadyInCall();
	}

	public boolean toggleEnableCamera() {
		if (mLc.inCall()) {
			boolean enabled = !mLc.getCurrentCall().cameraEnabled();
			enableCamera(mLc.getCurrentCall(), enabled);
			return enabled;
		}
		return false;
	}

	public void enableCamera(Call call, boolean enable) {
		if (call != null) {
			call.enableCamera(enable);
			if (mServiceContext.getResources().getBoolean(R.bool.enable_call_notification))
				LinphoneService.instance().refreshIncallIcon(mLc.getCurrentCall());
		}
	}

	public void sendStaticImage(boolean send) {
		if (mLc.inCall()) {
			enableCamera(mLc.getCurrentCall(), !send);
		}
	}

	public void playDtmf(ContentResolver r, char dtmf) {
		try {
			if (Settings.System.getInt(r, Settings.System.DTMF_TONE_WHEN_DIALING) == 0) {
				// audible touch disabled: don't play on speaker, only send in outgoing stream
				return;
			}
		} catch (SettingNotFoundException e) {}

		getLc().playDtmf(dtmf, -1);
	}

	public void terminateCall() {
		if (mLc.inCall()) {
			mLc.terminateCall(mLc.getCurrentCall());
		}
	}

	public void initTunnelFromConf() {
		if (!mLc.tunnelAvailable())
			return;

		/*NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
		mLc.tunnelCleanServers();
		TunnelConfig config = mPrefs.getTunnelConfig();
		if (config.getHost() != null) {
			mLc.tunnelAddServer(config);
			manageTunnelServer(info);
		}*/ // TODO FIXME
	}

	private boolean isTunnelNeeded(NetworkInfo info) {
		/*if (info == null) {
			Log.i("No connectivity: tunnel should be disabled");
			return false;
		}

		String pref = mPrefs.getTunnelMode();

		if (getString(R.string.tunnel_mode_entry_value_always).equals(pref)) {
			return true;
		}

		if (info.getType() != ConnectivityManager.TYPE_WIFI
				&& getString(R.string.tunnel_mode_entry_value_3G_only).equals(pref)) {
			Log.i("need tunnel: 'no wifi' connection");
			return true;
		}*/ // TODO FIXME

		return false;
	}

	private void manageTunnelServer(NetworkInfo info) {
		if (mLc == null) return;
		if (!mLc.tunnelAvailable()) return;

		/*Log.i("Managing tunnel");
		if (isTunnelNeeded(info)) {
			Log.i("Tunnel need to be activated");
			mLc.tunnelSetMode(Core.TunnelMode.enable);
		} else {
			Log.i("Tunnel should not be used");
			String pref = mPrefs.getTunnelMode();
			mLc.tunnelSetMode(Core.TunnelMode.disable);
			if (getString(R.string.tunnel_mode_entry_value_auto).equals(pref)) {
				mLc.tunnelSetMode(Core.TunnelMode.auto);
			}
		}*/ // TODO FIXME
	}

	public synchronized final void destroyCore() {
		sExited = true;
		ContactsManagerDestroy();
		BluetoothManagerDestroy();
		try {
			mTimer.cancel();
			mLc = null;
		}
		catch (RuntimeException e) {
			Log.e(e);
		}
		finally {
			try {
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
					mServiceContext.unregisterReceiver(mNetworkReceiver);
				}
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mHookReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mCallReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mKeepAliveReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				dozeManager(false);
			} catch (IllegalArgumentException iae) {
				Log.e(iae);
			}catch (Exception e) {
				Log.e(e);
			}
			mLc = null;
		}
	}

	public void restartCore() {
		destroyCore();
		startLibLinphone(mServiceContext);
		sExited = false;
	}

	private synchronized void startLibLinphone(Context c) {
		try {
			copyAssetsFromPackage();
			//traces alway start with traces enable to not missed first initialization
			mLc = Factory.instance().createCore(this, mConfigFile, mLinphoneFactoryConfigFile, c);
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					LinphoneUtils.dispatchOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (mLc != null) {
								mLc.iterate();
							}
						}
					});
				}
			};
			/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
			mTimer = new Timer("Linphone scheduler");
			mTimer.schedule(lTask, 0, 20);
		} catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}

	private void initPushNotificationsService() {
		if (getString(R.string.push_type).equals("google")) {
			try {
				Class<?> GCMRegistrar = Class.forName("com.google.android.gcm.GCMRegistrar");
				GCMRegistrar.getMethod("checkDevice", Context.class).invoke(null, mServiceContext);
				try {
					GCMRegistrar.getMethod("checkManifest", Context.class).invoke(null, mServiceContext);
				} catch (IllegalStateException e) {
					Log.e("[Push Notification] No receiver found", e);
				}
				final String regId = (String) GCMRegistrar.getMethod("getRegistrationId", Context.class).invoke(null, mServiceContext);
				String newPushSenderID = mServiceContext.getString(R.string.push_sender_id);
				String currentPushSenderID = LinphonePreferences.instance().getPushNotificationRegistrationID();
				if (regId.equals("") || currentPushSenderID == null || !currentPushSenderID.equals(newPushSenderID)) {
					GCMRegistrar.getMethod("register", Context.class, String[].class).invoke(null, mServiceContext, new String[]{newPushSenderID});
					Log.i("[Push Notification] Storing current sender id = " + newPushSenderID);
				} else {
					Log.i("[Push Notification] Already registered with id = " + regId);
					LinphonePreferences.instance().setPushNotificationRegistrationID(regId);
				}
			} catch (java.lang.UnsupportedOperationException e) {
				Log.i("[Push Notification] Not activated");
			} catch (Exception e1) {
				Log.i("[Push Notification] Assuming GCM jar is not provided.");
			}
		}
	}

	private synchronized void initLiblinphone(Core lc) throws CoreException {
		mLc = lc;

		mLc.setZrtpSecretsFile(basePath + "/zrtp_secrets");

		try {
			String versionName = mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionName;
			if (versionName == null) {
				versionName = String.valueOf(mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionCode);
			}
			mLc.setUserAgent("LinphoneAndroid", versionName);
		} catch (NameNotFoundException e) {
			Log.e(e, "cannot get version name");
		}

		mLc.setRingback(mRingbackSoundFile);
		mLc.setRootCa(mLinphoneRootCaFile);
		mLc.setPlayFile(mPauseSoundFile);
		mLc.setChatDatabasePath(mChatDatabaseFile);
		mLc.setCallLogsDatabasePath(mCallLogDatabaseFile);
		mLc.setFriendsDatabasePath(mFriendsDatabaseFile);
		mLc.setUserCertificatesPath(mUserCertificatePath);
		//mLc.setCallErrorTone(Reason.NotFound, mErrorToneFile);
		enableDeviceRingtone(mPrefs.isDeviceRingtoneEnabled());

		int availableCores = Runtime.getRuntime().availableProcessors();
		Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
		//mLc.setCpuCount(availableCores);

		mLc.migrateLogsFromRcToDb();

		if (mServiceContext.getResources().getBoolean(R.bool.enable_push_id)) {
			initPushNotificationsService();
		}

		/*
		 You cannot receive this through components declared in manifests, only
		 by explicitly registering for it with Context.registerReceiver(). This is a protected intent that can only
		 be sent by the system.
		*/
		mKeepAliveIntentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		mKeepAliveIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);

		mKeepAliveReceiver = new KeepAliveReceiver();
		mServiceContext.registerReceiver(mKeepAliveReceiver, mKeepAliveIntentFilter);

		mCallIntentFilter = new IntentFilter("android.intent.action.ACTION_NEW_OUTGOING_CALL");
		mCallIntentFilter.setPriority(99999999);
		mCallReceiver = new OutgoingCallReceiver();
		try {
			mServiceContext.registerReceiver(mCallReceiver, mCallIntentFilter);
		}catch(IllegalArgumentException e){e.printStackTrace();}
		mProximityWakelock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "manager_proximity_sensor");



		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			mDozeIntentFilter = new IntentFilter();
			mDozeIntentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
			mDozeReceiver = new DozeReceiver();
			dozeModeEnabled = ((PowerManager) mServiceContext.getSystemService(Context.POWER_SERVICE)).isDeviceIdleMode();
			mServiceContext.registerReceiver(mDozeReceiver, mDozeIntentFilter);
		}

		mHookIntentFilter = new IntentFilter("com.base.module.phone.HOOKEVENT");
		mHookIntentFilter.setPriority(999);
		mHookReceiver = new HookReceiver();
		mServiceContext.registerReceiver(mHookReceiver, mHookIntentFilter);

		// Since Android N we need to register the network manager
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
			mNetworkReceiver = new NetworkManager();
			mNetworkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
			mServiceContext.registerReceiver(mNetworkReceiver, mNetworkIntentFilter);
		}

		updateNetworkReachability();

		resetCameraFromPreferences();

		accountCreator = LinphoneManager.getLc().createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setListener(this);
		callGsmON = false;
	}

	public void setHandsetMode(Boolean on){
		if (mLc.isIncomingInvitePending() && on) {
			handsetON = true;
			mLc.acceptCall(mLc.getCurrentCall());
			LinphoneActivity.instance().startIncallActivity(mLc.getCurrentCall());
		} else if(on && CallActivity.isInstanciated()) {
			handsetON = true;
			CallActivity.instance().setSpeakerEnabled(true);
			CallActivity.instance().refreshInCallActions();
		} else if (!on) {
			handsetON = false;
			LinphoneManager.getInstance().terminateCall();
		}
	}

	public boolean isHansetModeOn(){
		return handsetON;
	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.notes_of_the_optimistic, mRingSoundFile);
		copyIfNotExist(R.raw.ringback, mRingbackSoundFile);
		copyIfNotExist(R.raw.hold, mPauseSoundFile);
		copyIfNotExist(R.raw.incoming_chat, mErrorToneFile);
		copyIfNotExist(R.raw.linphonerc_default, mConfigFile);
		copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
		copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
		copyFromPackage(R.raw.rootca, new File(mLinphoneRootCaFile).getName());
		copyFromPackage(R.raw.assistant_create, new File(mDynamicConfigFile).getName());
	}

	public void copyIfNotExist(int ressourceId, String target) throws IOException {
		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {
			copyFromPackage(ressourceId,lFileToCopy.getName());
		}
	}

	public void copyFromPackage(int ressourceId, String target) throws IOException{
		FileOutputStream lOutputStream = mServiceContext.openFileOutput (target, 0);
		InputStream lInputStream = mR.openRawResource(ressourceId);
		int readByte;
		byte[] buff = new byte[8048];
		while (( readByte = lInputStream.read(buff)) != -1) {
			lOutputStream.write(buff,0, readByte);
		}
		lOutputStream.flush();
		lOutputStream.close();
		lInputStream.close();
	}

	//public void loadConfig(){
	//	try {
	//		copyIfNotExist(R.raw.configrc, mConfigFile);
	//	} catch (Exception e){
	//		Log.w(e);
	//	}
	//	LinphonePreferences.instance().setRemoteProvisioningUrl("file://" + mConfigFile);
	//	getLc().getConfig().setInt("misc","transient_provisioning",1);
	//}


	public boolean detectVideoCodec(String mime) {
		for (PayloadType videoCodec : mLc.getVideoPayloadTypes()) {
			if (mime.equals(videoCodec.getMimeType())) return true;
		}
		return false;
	}

	public boolean detectAudioCodec(String mime){
		for (PayloadType audioCodec : mLc.getAudioPayloadTypes()) {
			if (mime.equals(audioCodec.getMimeType())) return true;
		}
		return false;
	}

	public void updateNetworkReachability() {
		if (mConnectivityManager == null) return;

		boolean connected = false;
		NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
		connected = networkInfo != null && networkInfo.isConnected();

		if (networkInfo == null && Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			for (Network network : mConnectivityManager.getAllNetworks()) {
				if (network != null) {
					networkInfo = mConnectivityManager.getNetworkInfo(network);
					if (networkInfo != null && networkInfo.isConnected()) {
						connected = true;
						break;
					}
				}
			}
		}

		if (networkInfo == null || !connected) {
			Log.i("No connectivity: setting network unreachable");
			mLc.setNetworkReachable(false);
		} else if (dozeModeEnabled) {
			Log.i("Doze Mode enabled: shutting down network");
			mLc.setNetworkReachable(false);
		} else if (connected){
			manageTunnelServer(networkInfo);

			boolean wifiOnly = LinphonePreferences.instance().isWifiOnlyEnabled();
			if (wifiOnly){
				if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					mLc.setNetworkReachable(true);
				}
				else {
					Log.i("Wifi-only mode, setting network not reachable");
					mLc.setNetworkReachable(false);
				}
			} else {
				int curtype=networkInfo.getType();

				if (curtype != mLastNetworkType) {
					//if kind of network has changed, we need to notify network_reachable(false) to make sure all current connections are destroyed.
					//they will be re-created during setNetworkReachable(true).
					Log.i("Connectivity has changed.");
					mLc.setNetworkReachable(false);
				}
				mLc.setNetworkReachable(true);
				mLastNetworkType = curtype;
			}
		}

		if (mLc.isNetworkReachable()) {
			// When network isn't available, push informations might not be set. This should fix the issue.
			LinphonePreferences prefs = LinphonePreferences.instance();
			prefs.setPushNotificationEnabled(prefs.isPushNotificationEnabled());
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void doDestroy() {
		ContactsManagerDestroy();
		BluetoothManagerDestroy();
		try {
			mTimer.cancel();
			mLc = null;
		}
		catch (RuntimeException e) {
			Log.e(e);
		}
		finally {
			try {
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
					mServiceContext.unregisterReceiver(mNetworkReceiver);
				}
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mHookReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mKeepAliveReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mCallReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				dozeManager(false);
			} catch (IllegalArgumentException iae) {
				Log.e(iae);
			} catch (Exception e) {
				Log.e(e);
			}
			mLc = null;
			instance = null;
		}
	}

	public void dozeManager(boolean enable) {
		if (enable) {
			Log.i("[Doze Mode]: register");
			mServiceContext.registerReceiver(mDozeReceiver, mDozeIntentFilter);
			dozeModeEnabled = true;
		} else {
			Log.i("[Doze Mode]: unregister");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				try {
					mServiceContext.unregisterReceiver(mDozeReceiver);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}
			}
			dozeModeEnabled = false;
		}
	}

	public void enableProximitySensing(boolean enable){
		if (enable){
			if (!mProximitySensingEnabled){
				mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
				mProximitySensingEnabled = true;
			}
		} else {
			if (mProximitySensingEnabled){
				mSensorManager.unregisterListener(this);
				mProximitySensingEnabled = false;
				// Don't forgeting to release wakelock if held
				if(mProximityWakelock.isHeld()) {
					mProximityWakelock.release();
				}
			}
		}
	}

	public static Boolean isProximitySensorNearby(final SensorEvent event) {
		float threshold = 4.001f; // <= 4 cm is near

		final float distanceInCm = event.values[0];
		final float maxDistance = event.sensor.getMaximumRange();
		Log.d("Proximity sensor report ["+distanceInCm+"] , for max range ["+maxDistance+"]");

		if (maxDistance <= threshold) {
			// Case binary 0/1 and short sensors
			threshold = maxDistance;
		}
		return distanceInCm < threshold;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.timestamp == 0) return;
		if(isProximitySensorNearby(event)){
			if(!mProximityWakelock.isHeld()) {
				mProximityWakelock.acquire();
			}
		} else {
			if(mProximityWakelock.isHeld()) {
				mProximityWakelock.release();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public static void ContactsManagerDestroy() {
		if (LinphoneManager.instance != null && LinphoneManager.instance.mServiceContext != null)
			LinphoneManager.instance.mServiceContext.getContentResolver().unregisterContentObserver(ContactsManager.getInstance());
		ContactsManager.getInstance().destroy();
	}

	public static void BluetoothManagerDestroy() {
		BluetoothManager.getInstance().destroy();
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		getInstance().changeStatusToOffline();
		sExited = true;
		instance.doDestroy();
	}

	private String getString(int key) {
		return mR.getString(key);
	}

	/* Simple implementation as Android way seems very complicate:
	For example: with wifi and mobile actives; when pulling mobile down:
	I/Linphone( 8397): WIFI connected: setting network reachable
	I/Linphone( 8397): new state [RegistrationProgress]
	I/Linphone( 8397): mobile disconnected: setting network unreachable
	I/Linphone( 8397): Managing tunnel
	I/Linphone( 8397): WIFI connected: setting network reachable
	*/
	public void connectivityChanged(ConnectivityManager cm, boolean noConnectivity) {
		updateNetworkReachability();
	}

	public interface EcCalibrationListener {
		void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs);
	}

	private Call ringingCall;

	private MediaPlayer mRingerPlayer;
	private Vibrator mVibrator;

	public void onNewSubscriptionRequested(Core lc, Friend lf, String url) {}
	public void onNotifyPresenceReceived(Core lc, Friend lf) {
		ContactsManager.getInstance().refreshSipContact(lf);
	}

	@Override
	public void onEcCalibrationAudioInit(Core lc) {

	}

	@Override
	public void onDtmfReceived(Core lc, Call call, int dtmf) {
		Log.d("DTMF received: " + dtmf);
	}

	@Override
	public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
		if (mServiceContext.getResources().getBoolean(R.bool.disable_chat)) {
			return;
		}

		if (mCurrentChatRoomAddress != null && cr.getPeerAddress().asStringUriOnly().equals(mCurrentChatRoomAddress.asStringUriOnly())) {
			Log.i("Message received for currently displayed chat room, do not make a notification");
			return;
		}

		Address from = message.getFromAddress();
		LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(from);
		String textMessage = (message.getFileTransferInformation() != null) ? getString(R.string.content_description_incoming_file) : message.getText();

		if (!mServiceContext.getResources().getBoolean(R.bool.disable_chat_message_notification)) {
			if (!cr.canHandleParticipants() || (cr.getNbParticipants() == 1 && getString(R.string.dummy_group_chat_subject).equals(cr.getSubject()))) {
				if (contact != null) {
					LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(), contact.getFullName(), contact.getThumbnailUri(), textMessage);
				} else {
					LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(), from.getUsername(), null, textMessage);
				}
			} else {
				String subject = cr.getSubject();
				if (contact != null) {
					LinphoneService.instance().displayGroupChatMessageNotification(subject, cr.getConferenceAddress().asString(), contact.getFullName(), contact.getThumbnailUri(), textMessage);
				} else {
					LinphoneService.instance().displayGroupChatMessageNotification(subject, cr.getConferenceAddress().asString(), from.getUsername(), null, textMessage);
				}
			}
		}
	}

	public void setCurrentChatRoomAddress(Address address) {
		mCurrentChatRoomAddress = address;
	}

	@Override
	public void onEcCalibrationResult(Core lc, EcCalibratorStatus status, int delay_ms) {
		((AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
		mAudioManager.abandonAudioFocus(null);
		Log.i("Set audio mode on 'Normal'");
	}

	public void onGlobalStateChanged(final Core lc, final GlobalState state, final String message) {
		Log.i("New global state [",state,"]");
		if (state == Core.GlobalState.On){
			try {
				Log.e("LinphoneManager"," onGlobalStateChanged ON");
				initLiblinphone(lc);

			}catch(IllegalArgumentException iae){
				Log.e(iae);
			} catch (CoreException e) {
				Log.e(e);
			}
		}
	}

	public void onRegistrationStateChanged(final Core lc, final ProxyConfig proxy,final RegistrationState state,final String message) {
		Log.i("New registration state ["+state+"]");
		if(LinphoneManager.getLc().getDefaultProxyConfig() == null){
			subscribeFriendList(false);
		}
	}

	private int savedMaxCallWhileGsmIncall;
	private synchronized void preventSIPCalls() {
		if (savedMaxCallWhileGsmIncall != 0) {
			Log.w("SIP calls are already blocked due to GSM call running");
			return;
		}
		savedMaxCallWhileGsmIncall = mLc.getMaxCalls();
		mLc.setMaxCalls(0);
	}
	private synchronized void allowSIPCalls() {
		if (savedMaxCallWhileGsmIncall == 0) {
			Log.w("SIP calls are already allowed as no GSM call known to be running");
			return;
		}
		mLc.setMaxCalls(savedMaxCallWhileGsmIncall);
		savedMaxCallWhileGsmIncall = 0;
	}
	public static void setGsmIdle(boolean gsmIdle) {
		LinphoneManager mThis = instance;
		if (mThis == null) return;
		if (gsmIdle) {
			mThis.allowSIPCalls();
		} else {
			mThis.preventSIPCalls();
		}
	}

	public Context getContext() {
		try {
			if (LinphoneActivity.isInstanciated())
				return LinphoneActivity.instance();
			else if (CallActivity.isInstanciated())
				return CallActivity.instance();
			else if (CallIncomingActivity.isInstanciated())
				return CallIncomingActivity.instance();
			else if (mServiceContext != null)
				return mServiceContext;
			else if (LinphoneService.isReady())
				return LinphoneService.instance().getApplicationContext();
		} catch (Exception e) {
			Log.e(e);
		}
		return null;
	}

	public void setAudioManagerInCallMode() {
		if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
			Log.w("[AudioManager] already in MODE_IN_COMMUNICATION, skipping...");
			return;
		}
		Log.d("[AudioManager] Mode: MODE_IN_COMMUNICATION");

		mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
	}

	@SuppressLint("Wakelock")
	public void onCallStateChanged(final Core lc,final Call call, final State state, final String message) {
		Log.i("New call state [",state,"]");
		if (state == State.IncomingReceived && !call.equals(lc.getCurrentCall())) {
			if (call.getReplacedCall()!=null){
				// attended transfer
				// it will be accepted automatically.
				return;
			}
		}

		if (state == State.IncomingReceived && getCallGsmON()) {
			if (mLc != null) {
				mLc.declineCall(call, Reason.Busy);
			}
		} else if (state == State.IncomingReceived && (LinphonePreferences.instance().isAutoAnswerEnabled()) && !getCallGsmON()) {
			TimerTask lTask = new TimerTask() {
					@Override
					public void run() {
					if (mLc != null) {
						if (mLc.getCallsNb() > 0) {
							mLc.acceptCall(call);
							if (LinphoneManager.getInstance() != null) {
								LinphoneManager.getInstance().routeAudioToReceiver();
								if (LinphoneActivity.instance() != null)
									LinphoneActivity.instance().startIncallActivity(call);
							}
						}
					}
				}
			};
			mTimer = new Timer("Auto answer");
			mTimer.schedule(lTask, mPrefs.getAutoAnswerTime());
		} else if (state == State.IncomingReceived || (state == State.IncomingEarlyMedia && mR.getBoolean(R.bool.allow_ringing_while_early_media))) {
			// Brighten screen for at least 10 seconds
			if (mLc.getCallsNb() == 1) {
				requestAudioFocus(STREAM_RING);

				ringingCall = call;
				startRinging();
				// otherwise there is the beep
			}
		} else if (call == ringingCall && isRinging) {
			//previous state was ringing, so stop ringing
			stopRinging();
		}

		if (state == State.Connected) {
			if (mLc.getCallsNb() == 1) {
				//It is for incoming calls, because outgoing calls enter MODE_IN_COMMUNICATION immediately when they start.
				//However, incoming call first use the MODE_RINGING to play the local ring.
				if(call.getDir() == Call.Dir.Incoming) {
					setAudioManagerInCallMode();
					//mAudioManager.abandonAudioFocus(null);
					requestAudioFocus(STREAM_VOICE_CALL);
				}
			}

			if (Hacks.needSoftvolume()) {
				Log.w("Using soft volume audio hack");
				adjustVolume(0); // Synchronize
			}
		}

		if (state == State.End || state == State.Error) {
			if (mLc.getCallsNb() == 0) {
				//Disabling proximity sensor
				enableProximitySensing(false);
				Context activity = getContext();
				if (mAudioFocused) {
					int res = mAudioManager.abandonAudioFocus(null);
					Log.d("Audio focus released a bit later: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
					mAudioFocused = false;
				}
				if (activity != null) {
					TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
					if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
						Log.d("---AudioManager: back to MODE_NORMAL");
						mAudioManager.setMode(AudioManager.MODE_NORMAL);
						Log.d("All call terminated, routing back to earpiece");
						routeAudioToReceiver();
					}
				}
			}
		}
		if (state == State.UpdatedByRemote) {
			// If the correspondent proposes video while audio call
			boolean remoteVideo = call.getRemoteParams().videoEnabled();
			boolean localVideo = call.getCurrentParams().videoEnabled();
			boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
			if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !(LinphoneManager.getLc().getConference() != null)) {
				LinphoneManager.getLc().deferCallUpdate(call);
			}
		}
		if (state == State.OutgoingInit) {
			//Enter the MODE_IN_COMMUNICATION mode as soon as possible, so that ringback
			//is heard normally in earpiece or bluetooth receiver.
			setAudioManagerInCallMode();
			requestAudioFocus(STREAM_VOICE_CALL);
			startBluetooth();
		}

		if (state == State.StreamsRunning) {
			startBluetooth();
			setAudioManagerInCallMode();
		}
	}

	public void startBluetooth() {
		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			BluetoothManager.getInstance().routeAudioToBluetooth();
		}
	}

	public void onCallStatsUpdated(final Core lc, final Call call, final CallStats stats) {}

	public void onCallEncryptionChanged(Core lc, Call call,
			boolean encrypted, String authenticationToken) {
	}

	public void startEcCalibration() throws CoreException {
		routeAudioToSpeaker();
		setAudioManagerInCallMode();
		Log.i("Set audio mode on 'Voice Communication'");
		requestAudioFocus(STREAM_VOICE_CALL);
		int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
		int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
		mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
		mLc.startEchoCancellerCalibration();
		mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
	}

	public int startEchoTester() throws CoreException {
		routeAudioToSpeaker();
		setAudioManagerInCallMode();
		Log.i("Set audio mode on 'Voice Communication'");
		requestAudioFocus(STREAM_VOICE_CALL);
		int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
		int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
		int sampleRate = 44100;
		mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			String sampleRateProperty = mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
			sampleRate = Integer.parseInt(sampleRateProperty);
		}
		/*int status = */mLc.startEchoTester(sampleRate);
		/*if (status > 0)*/
			echoTesterIsRunning = true;
		/*else {
			echoTesterIsRunning = false;
			routeAudioToReceiver();
			mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
			((AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
			Log.i("Set audio mode on 'Normal'");
		}*/
		return 1;
		//return status;
	}

	public int stopEchoTester() throws CoreException {
		echoTesterIsRunning = false;
		/*int status = */mLc.stopEchoTester();
		routeAudioToReceiver();
		((AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
		Log.i("Set audio mode on 'Normal'");
		return 1;//status;
	}

	public boolean getEchoTesterStatus() {
		return echoTesterIsRunning;
	}

	private boolean isRinging;

	private void requestAudioFocus(int stream){
		if (!mAudioFocused){
			int res = mAudioManager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT );
			Log.d("Audio focus requested: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
			if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused=true;
		}
	}

	public void enableDeviceRingtone(boolean use) {
		if (use) {
			mLc.setRing(null);
		} else {
			mLc.setRing(mRingSoundFile);
		}
	}

	private synchronized void startRinging()  {
		if (!LinphonePreferences.instance().isDeviceRingtoneEnabled()) {
			// Enable speaker audio route, linphone library will do the ringing itself automatically
			routeAudioToSpeaker();
			return;
		}

		if (mR.getBoolean(R.bool.allow_ringing_while_early_media)) {
			routeAudioToSpeaker(); // Need to be able to ear the ringtone during the early media
		}

		//if (Hacks.needGalaxySAudioHack())
		mAudioManager.setMode(MODE_RINGTONE);

		try {
			if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && mVibrator != null) {
				long[] patern = {0,1000,1000};
				mVibrator.vibrate(patern, 1);
			}
			if (mRingerPlayer == null) {
				requestAudioFocus(STREAM_RING);
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(STREAM_RING);

				String ringtone = LinphonePreferences.instance().getRingtone(Settings.System.DEFAULT_RINGTONE_URI.toString());
				try {
					if (ringtone.startsWith("content://")) {
						mRingerPlayer.setDataSource(mServiceContext, Uri.parse(ringtone));
					} else {
						FileInputStream fis = new FileInputStream(ringtone);
						mRingerPlayer.setDataSource(fis.getFD());
						fis.close();
					}
				} catch (IOException e) {
					Log.e(e, "Cannot set ringtone");
				}

				mRingerPlayer.prepare();
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w("already ringing");
			}
		} catch (Exception e) {
			Log.e(e,"cannot handle incoming call");
		}
		isRinging = true;
	}

	private synchronized void stopRinging() {
		if (mRingerPlayer != null) {
			mRingerPlayer.stop();
			mRingerPlayer.release();
			mRingerPlayer = null;
		}
		if (mVibrator != null) {
			mVibrator.cancel();
		}

		if (Hacks.needGalaxySAudioHack())
			mAudioManager.setMode(AudioManager.MODE_NORMAL);

		isRinging = false;
		// You may need to call galaxys audio hack after this method
		if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			if (mServiceContext.getResources().getBoolean(R.bool.isTablet)) {
				Log.d("Stopped ringing, routing back to speaker");
				routeAudioToSpeaker();
			} else {
				Log.d("Stopped ringing, routing back to earpiece");
				routeAudioToReceiver();
			}
		}
	}


	public static String extractADisplayName(Resources r, Address address) {
		if (address == null) return r.getString(R.string.unknown_incoming_call_name);

		final String displayName = address.getDisplayName();
		if (displayName!=null) {
			return displayName;
		} else  if (address.getUsername() != null){
			return address.getUsername();
		} else {
			String rms = address.toString();
			if (rms != null && rms.length() > 1)
				return rms;

			return r.getString(R.string.unknown_incoming_call_name);
		}
	}

	public static boolean reinviteWithVideo() {
		return CallManager.getInstance().reinviteWithVideo();
	}

	/**
	 *
	 * @return false if already in video call.
	 */
	public boolean addVideo() {
		Call call = mLc.getCurrentCall();
		enableCamera(call, true);
		return reinviteWithVideo();
	}

	public boolean acceptCallIfIncomingPending() throws CoreException {
		if (mLc.isIncomingInvitePending()) {
			mLc.acceptCall(mLc.getCurrentCall());
			return true;
		}
		return false;
	}

	public boolean acceptCall(Call call) {
		mLc.acceptCall(call);
		return true;
	}

	public boolean acceptCallWithParams(Call call, CallParams params) {
		mLc.acceptCallWithParams(call, params);
		return true;
	}

	public static String extractIncomingRemoteName(Resources r, Address linphoneAddress) {
		return extractADisplayName(r, linphoneAddress);
	}

	public void adjustVolume(int i) {
		if (Build.VERSION.SDK_INT < 15) {
			int oldVolume = mAudioManager.getStreamVolume(LINPHONE_VOLUME_STREAM);
			int maxVolume = mAudioManager.getStreamMaxVolume(LINPHONE_VOLUME_STREAM);

			int nextVolume = oldVolume +i;
			if (nextVolume > maxVolume) nextVolume = maxVolume;
			if (nextVolume < 0) nextVolume = 0;

			mLc.setPlaybackGainDb((nextVolume - maxVolume)* dbStep);
		} else
			// starting from ICS, volume must be adjusted by the application, at least for STREAM_VOICE_CALL volume stream
			mAudioManager.adjustStreamVolume(LINPHONE_VOLUME_STREAM, i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
	}

	public static synchronized Core getLcIfManagerNotDestroyedOrNull() {
		if (sExited || instance == null) {
			// Can occur if the UI thread play a posted event but in the meantime the LinphoneManager was destroyed
			// Ex: stop call and quickly terminate application.
			return null;
		}
		return getLc();
	}

	public static final boolean isInstanciated() {
		return instance != null;
	}

	public synchronized Call getPendingIncomingCall() {
		Call currentCall = mLc.getCurrentCall();
		if (currentCall == null) return null;

		Call.State state = currentCall.getState();
		boolean incomingPending = currentCall.getDir() == Call.Dir.Incoming
			&& (state == State.IncomingReceived || state == State.IncomingEarlyMedia);

		return incomingPending ? currentCall : null;
	}

	public void displayLinkPhoneNumber() {
		accountCreator.setUsername(LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()));
		accountCreator.isAccountLinked();
	}

	public void isAccountWithAlias(){
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null) {
			long now = new Timestamp(new Date().getTime()).getTime();
			if (LinphonePreferences.instance().getLinkPopupTime() == null
					|| Long.parseLong(LinphonePreferences.instance().getLinkPopupTime()) < now) {
				accountCreator.setUsername(LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()));
				accountCreator.isAccountExist();
			}
		} else {
			LinphonePreferences.instance().setLinkPopupTime(null);
		}
	}

	private void askLinkWithPhoneNumber(){
		long now = new Timestamp(new Date().getTime()).getTime();
		long future = new Timestamp(LinphoneActivity.instance().getResources().getInteger(R.integer.popup_time_interval)).getTime();
		long newDate = now + future;

		LinphonePreferences.instance().setLinkPopupTime(String.valueOf(newDate));

		final Dialog dialog = LinphoneActivity.instance().displayDialog(String.format(getString(R.string.link_account_popup), LinphoneManager.getLc().getDefaultProxyConfig().getIdentityAddress().asStringUriOnly()));
		Button delete = (Button) dialog.findViewById(R.id.delete_button);
		delete.setText(getString(R.string.link));
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setText(getString(R.string.maybe_later));

		delete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent assistant = new Intent();
				assistant.setClass(LinphoneActivity.instance(), AssistantActivity.class);
				assistant.putExtra("LinkPhoneNumber", true);
				assistant.putExtra("LinkPhoneNumberAsk", true);
				mServiceContext.startActivity(assistant);
				dialog.dismiss();
			}
		});

		LinphonePreferences.instance().setLinkPopupTime(String.valueOf(newDate));

		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				dialog.dismiss();
			}
		});
		dialog.show();
	}

	public void setDozeModeEnabled(boolean b) {
		dozeModeEnabled = b;
	}

	public String getmDynamicConfigFile() {
		return mDynamicConfigFile;
	}

	public boolean getCallGsmON() {
		return callGsmON;
	}

	public void setCallGsmON(boolean on) {
		callGsmON = on;
	}

	@Override
	public void onTransferStateChanged(Core lc, Call call,
			State new_call_state) {

	}

	@Override
	public void onChatRoomInstantiated(Core lc, ChatRoom cr) {

	}

	@Override
	public void onInfoReceived(Core lc, Call call, InfoMessage info) {
		Log.d("Info message received from "+call.getRemoteAddress().asString());
		Content ct=info.getContent();
		if (ct!=null){
			Log.d("Info received with body with mime type "+ct.getType()+"/"+ct.getSubtype()+" and data ["+ct.getStringBuffer()+"]");
		}
	}
	@Override
	public void onSubscriptionStateChanged(Core lc, Event ev,
			SubscriptionState state) {
		Log.d("Subscription state changed to "+state+" event name is "+ev.getName());
	}

	@Override
	public void onCallLogUpdated(Core lc, CallLog newcl) {

	}

	@Override
	public void onNotifyReceived(Core lc, Event ev,
			String eventName, Content content) {
		Log.d("Notify received for event "+eventName);
		if (content!=null) Log.d("with content "+content.getType()+"/"+content.getSubtype()+" data:"+content.getStringBuffer());
	}
	@Override
	public void onPublishStateChanged(Core lc, Event ev, Event.PublishState state) {
		Log.d("Publish state changed to " + state + " for event name " + ev.getName());
	}

	@Override
	public void onIsComposingReceived(Core lc, ChatRoom cr) {
		Log.d("Composing received for chatroom " + cr.getPeerAddress().asStringUriOnly());
	}

	@Override
	public void onMessageReceivedUnableDecrypt(Core lc, ChatRoom room, ChatMessage message) {

	}

	@Override
	public void onConfiguringStatus(Core lc,
			ConfiguringState state, String message) {
		Log.d("Remote provisioning status = " + state.toString() + " (" + message + ")");

		if (state == ConfiguringState.Successful) {
			if (LinphonePreferences.instance().isProvisioningLoginViewEnabled()) {
				ProxyConfig proxyConfig = lc.createProxyConfig();
				Address addr = proxyConfig.getIdentityAddress();
				wizardLoginViewDomain = addr.getDomain();
			}
		}
	}

	@Override
	public void onCallCreated(Core lc, Call call) {

	}

	@Override
	public void onLogCollectionUploadProgressIndication(Core linphoneCore, int offset, int total) {
		if(total > 0)
			Log.d("Log upload progress: currently uploaded = " + offset + " , total = " + total + ", % = " + String.valueOf((offset * 100) / total));
	}

	@Override
	public void onVersionUpdateCheckResultReceived(Core lc, Core.VersionUpdateCheckResult result, String version, String url) {

	}

	@Override
	public void onEcCalibrationAudioUninit(Core lc) {

	}

	@Override
	public void onLogCollectionUploadStateChanged(Core linphoneCore, LogCollectionUploadState state, String info) {
		Log.d("Log upload state: " + state.toString() + ", info = " + info);
	}

	@Override
	public void onFriendListCreated(Core lc, FriendList list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onFriendListRemoved(Core lc, FriendList list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onReferReceived(Core lc, String refer_to) {

	}

	@Override
	public void onNetworkReachable(Core lc, boolean enable) {

	}

	@Override
	public void onAuthenticationRequested(Core lc,
			AuthInfo authInfo, AuthMethod method) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNotifyPresenceReceivedForUriOrTel(Core lc, Friend lf, String uri_or_tel, PresenceModel presence_model) {

	}

	@Override
	public void onBuddyInfoUpdated(Core lc, Friend lf) {

	}

	@Override
	public void onIsAccountExist(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
		if (status.equals(AccountCreator.Status.AccountExist)) {
			accountCreator.isAccountLinked();
		}
	}

	@Override
	public void onCreateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onActivateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onLinkAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
		if (status.equals(AccountCreator.Status.AccountNotLinked)) {
			askLinkWithPhoneNumber();
		}
	}

	@Override
	public void onActivateAlias(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onIsAccountActivated(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onRecoverAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onIsAccountLinked(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
		if (status.equals(AccountCreator.Status.AccountNotLinked)) {
			askLinkWithPhoneNumber();
		}
	}

	@Override
	public void onIsAliasUsed(AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

	@Override
	public void onUpdateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}
}
