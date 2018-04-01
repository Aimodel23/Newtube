package org.schabi.newpipe.fragments.detail;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.nirhart.parallaxscroll.views.ParallaxScrollView;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.MainPlayerService;
import org.schabi.newpipe.player.VideoPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.*;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SettingsContentObserver;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import icepick.State;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener, View.OnLongClickListener,
        PlayerEventListener, PlayerServiceEventListener,
        SettingsContentObserver.OnChangeListener {
    private static final String TAG = ".VideoDetailFragment";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    public static final String AUTO_PLAY = "auto_play";

    // Amount of videos to show on start
    private static final int INITIAL_RELATED_VIDEOS = 8;

    private ArrayList<VideoStream> sortedStreamVideosList;

    private InfoItemBuilder infoItemBuilder = null;

    private int updateFlags = 0;
    private boolean isPaused;
    private static final int RELATED_STREAMS_UPDATE_FLAG = 0x1;
    private static final int RESOLUTIONS_MENU_UPDATE_FLAG = 0x2;
    private static final int TOOLBAR_ITEMS_UPDATE_FLAG = 0x4;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;
    private boolean wasRelatedStreamsExpanded = false;

    private String oldSelectedStreamResolution = null;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;
    @State
    protected PlayQueue playQueue;
    @State
    protected boolean fromBackStack;

    private StreamInfo currentInfo;
    private Disposable currentWorker;
    private CompositeDisposable disposables = new CompositeDisposable();
    BroadcastReceiver broadcastReceiver;
    public static final String ACTION_HIDE_MAIN_PLAYER = "org.schabi.newpipe.fragments.detail.VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER";

    private int selectedVideoStream = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private Menu menu;

    private Spinner spinnerToolbar;

    private ParallaxScrollView parallaxScrollRootView;
    private LinearLayout contentRootLayoutHiding;

    private View thumbnailBackgroundButton;
    private ImageView thumbnailImageView;
    private ImageView thumbnailPlayButton;

    private View videoTitleRoot;
    private TextView videoTitleTextView;
    private ImageView videoTitleToggleArrow;
    private TextView videoCountView;

    private TextView detailControlsBackground;
    private TextView detailControlsPopup;
    private TextView detailControlsAddToPlaylist;
    private TextView detailControlsDownload;
    private TextView appendControlsDetail;
    private TextView detailDurationView;

    private LinearLayout videoDescriptionRootLayout;
    private TextView videoUploadDateView;
    private TextView videoDescriptionView;

    private View uploaderRootLayout;
    private TextView uploaderTextView;
    private ImageView uploaderThumb;

    private TextView thumbsUpTextView;
    private ImageView thumbsUpImageView;
    private TextView thumbsDownTextView;
    private ImageView thumbsDownImageView;
    private TextView thumbsDisabledTextView;

    private TextView nextStreamTitle;
    private LinearLayout relatedStreamRootLayout;
    private LinearLayout relatedStreamsView;
    private ImageButton relatedStreamExpandButton;

    public int position = 0;
    private MainPlayerService playerService;

    private SettingsContentObserver settingsContentObserver;

    private ServiceConnection serviceConnection;
    private boolean bounded;
    private boolean shouldOpenPlayerAfterServiceConnects;
    private VideoPlayerImpl player;

    private ServiceConnection getServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (DEBUG)
                    Log.d(TAG, "Player service is disconnected");

                unbind();
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (DEBUG)
                    Log.d(TAG, "Player service is connected");

                MainPlayerService.LocalBinder localBinder = (MainPlayerService.LocalBinder)
                        service;

                playerService = localBinder.getService();
                player = localBinder.getPlayer();

                startPlayerListener();

                // It will do nothing if the player are not in fullscreen mode
                hideActionBar();
                hideSystemUi();

                if (currentInfo == null)
                    selectAndLoadVideo(serviceId, url, "", playQueue);

                if (!player.videoPlayerSelected())
                    return;

                if (player.getPlayQueue() != null)
                    addVideoPlayerView();

                boolean isLandscape = getResources().getDisplayMetrics().heightPixels < getResources().getDisplayMetrics().widthPixels;
                if (isLandscape) {
                    checkLandscape();
                } else if (player.isInFullscreen())
                    player.onFullScreenButtonClicked();

                if (currentInfo != null && isAutoplayEnabled() && player.getParentActivity() == null || shouldOpenPlayerAfterServiceConnects) {
                    setupMainPlayer();
                    shouldOpenPlayerAfterServiceConnects = false;
                }
            }
        };
    }

    private void bind() {
        if (DEBUG)
            Log.d(TAG, "bind() called");

        Intent serviceIntent = new Intent(getContext(), MainPlayerService.class);
        final boolean success = getActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        if (!success) {
            getActivity().unbindService(serviceConnection);
        }
        bounded = success;
    }

    private void unbind() {
        if (DEBUG)
            Log.d(TAG, "unbind() called");

        if (bounded) {
            getActivity().unbindService(serviceConnection);
            bounded = false;
            stopPlayerListener();
            playerService = null;
            player = null;
        }
    }

    private void startPlayerListener() {
        if (player != null)
            player.setFragmentListener(this);
    }

    private void stopPlayerListener() {
        if (player != null)
            player.removeFragmentListener(this);
    }

    private void startService() {
        getActivity().startService(new Intent(getActivity(), MainPlayerService.class));
        serviceConnection = getServiceConnection();
        bind();
    }

    private void stopService() {
        getActivity().stopService(new Intent(getActivity(), MainPlayerService.class));
        unbind();
    }

    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(int serviceId, String videoUrl, String name, PlayQueue playQueue) {
        VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name, playQueue);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        ThemeHelper.setTheme(getContext());
        if (isAutoplayPreferred())
            setAutoplay(true);

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_next_video_key), true);
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);

        startService();
        setupBroadcastReceiver();
        settingsContentObserver = new SettingsContentObserver(new Handler(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        isPaused = true;
        if (currentWorker != null)
            currentWorker.dispose();

        setupBrightness(true);
        getActivity().getContentResolver().unregisterContentObserver(settingsContentObserver);
    }

    @Override
    public void onResume() {
        super.onResume();
        isPaused = false;
        getActivity().getContentResolver().registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true,
                settingsContentObserver);

        setupBrightness(false);

        if (updateFlags != 0) {
            if (!isLoading.get() && currentInfo != null) {
                if ((updateFlags & RELATED_STREAMS_UPDATE_FLAG) != 0)
                    initRelatedVideos(currentInfo);
                if ((updateFlags & RESOLUTIONS_MENU_UPDATE_FLAG) != 0)
                    setupActionBar(currentInfo);
            }

            if ((updateFlags & TOOLBAR_ITEMS_UPDATE_FLAG) != 0 && menu != null)
                updateMenuItemVisibility();

            updateFlags = 0;
        }

        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            selectAndLoadVideo(serviceId, url, name, playQueue);
        }

        if (player != null && player.videoPlayerSelected())
            addVideoPlayerView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbind();

        if (currentWorker != null)
            currentWorker.dispose();
        if (disposables != null)
            disposables.clear();

        currentWorker = null;
        disposables = null;
        infoItemBuilder = null;
        currentInfo = null;
        sortedStreamVideosList = null;
        spinnerToolbar = null;

        getActivity().unregisterReceiver(broadcastReceiver);
        PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroyView() {
        if (DEBUG)
            Log.d(TAG, "onDestroyView() called");

        spinnerToolbar.setOnItemSelectedListener(null);
        spinnerToolbar.setAdapter(null);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, name);
                } else
                    Log.e(TAG, "ReCaptcha failed");
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedStreams = sharedPreferences.getBoolean(key, true);
            updateFlags |= RELATED_STREAMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.default_video_format_key))
                || key.equals(getString(R.string.default_resolution_key))
                || key.equals(getString(R.string.show_higher_resolutions_key))
                || key.equals(getString(R.string.use_external_video_player_key))) {
            updateFlags |= RESOLUTIONS_MENU_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_play_with_kodi_key))) {
            updateFlags |= TOOLBAR_ITEMS_UPDATE_FLAG;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    private static final String INFO_KEY = "info_key";
    private static final String STACK_KEY = "stack_key";
    private static final String WAS_RELATED_EXPANDED_KEY = "was_related_expanded_key";
    private static final String SELECTED_STREAM_RESOLUTION_KEY = "selected_stream_resolution_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        int nextCount = currentInfo != null && currentInfo.getNextVideo() != null ? 2 : 0;
        if (relatedStreamsView != null
                && relatedStreamsView.getChildCount() > INITIAL_RELATED_VIDEOS + nextCount) {
            outState.putSerializable(WAS_RELATED_EXPANDED_KEY, true);
        }

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        if (playQueue != null) {
            outState.putSerializable(VideoPlayer.PLAY_QUEUE, playQueue);
        }

        outState.putSerializable(STACK_KEY, stack);
        outState.putString(SELECTED_STREAM_RESOLUTION_KEY, oldSelectedStreamResolution);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        wasRelatedStreamsExpanded = savedState.getBoolean(WAS_RELATED_EXPANDED_KEY, false);
        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof StreamInfo) {
            //noinspection unchecked
            currentInfo = (StreamInfo) serializable;
            InfoCache.getInstance().putInfo(serviceId, url, currentInfo);
        }

        serializable = savedState.getSerializable(STACK_KEY);
        if (serializable instanceof Collection) {
            //noinspection unchecked
            stack.addAll((Collection<? extends StackItem>) serializable);
        }
        oldSelectedStreamResolution = savedState.getString(SELECTED_STREAM_RESOLUTION_KEY);
        playQueue = (PlayQueue) savedState.getSerializable(VideoPlayer.PLAY_QUEUE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(View v) {
        if (isLoading.get() || currentInfo == null)
            return;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFragmentManager() != null && currentInfo != null) {
                    PlaylistAppendDialog.fromStreamInfo(currentInfo)
                            .show(getFragmentManager(), TAG);
                }
                break;
            case R.id.detail_controls_download:
                if (!PermissionHelper.checkStoragePermissions(activity)) {
                    return;
                }

                try {
                    DownloadDialog downloadDialog =
                            DownloadDialog.newInstance(currentInfo,
                                    sortedStreamVideosList,
                                    selectedVideoStream);
                    downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
                } catch (Exception e) {
                    Toast.makeText(activity,
                            R.string.could_not_setup_download_menu,
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
                    Log.w(TAG, "Can't open channel because we got no channel URL");
                } else {
                    hideMainPlayer();
                    NavigationHelper.openChannelFragment(
                            getFragmentManager(),
                            currentInfo.getServiceId(),
                            currentInfo.getUploaderUrl(),
                            currentInfo.getUploaderName());
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                if (currentInfo.getVideoStreams().isEmpty()
                        && currentInfo.getVideoOnlyStreams().isEmpty()) {
                    openBackgroundPlayer(false);
                } else {
                    openVideoPlayer();
                }
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndDescription();
                break;
            case R.id.detail_related_streams_expand:
                toggleExpandRelatedVideos(currentInfo);
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (isLoading.get() || currentInfo == null)
            return false;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
        }

        return true;
    }

    private void toggleTitleAndDescription() {
        if (videoDescriptionRootLayout.getVisibility() == View.VISIBLE) {
            videoTitleTextView.setMaxLines(1);
            videoDescriptionRootLayout.setVisibility(View.GONE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        } else {
            videoTitleTextView.setMaxLines(10);
            videoDescriptionRootLayout.setVisibility(View.VISIBLE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_up);
        }
    }

    private void toggleExpandRelatedVideos(StreamInfo info) {
        if (DEBUG)
            Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [" + info + "]");

        if (!showRelatedStreams || info == null)
            return;

        int nextCount = info.getNextVideo() != null ? 2 : 0;
        int initialCount = INITIAL_RELATED_VIDEOS + nextCount;

        if (relatedStreamsView.getChildCount() > initialCount) {
            relatedStreamsView.removeViews(initialCount,
                    relatedStreamsView.getChildCount() - (initialCount));
            relatedStreamExpandButton.setImageDrawable(ContextCompat.getDrawable(
                    activity, ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.expand)));
            return;
        }

        //Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [" + info + "], from = [" + INITIAL_RELATED_VIDEOS + "]");
        for (int i = INITIAL_RELATED_VIDEOS; i < info.getRelatedStreams().size(); i++) {
            InfoItem item = info.getRelatedStreams().get(i);
            //Log.d(TAG, "i = " + i);
            relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
        }
        relatedStreamExpandButton.setImageDrawable(
                ContextCompat.getDrawable(activity,
                        ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.collapse)));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        spinnerToolbar = activity.findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner);

        parallaxScrollRootView = rootView.findViewById(R.id.detail_main_content);

        thumbnailBackgroundButton = rootView.findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailImageView = rootView.findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = rootView.findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayoutHiding = rootView.findViewById(R.id.detail_content_root_hiding);

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = rootView.findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = rootView.findViewById(R.id.detail_toggle_description_view);
        videoCountView = rootView.findViewById(R.id.detail_view_count_view);

        detailControlsBackground = rootView.findViewById(R.id.detail_controls_background);
        detailControlsPopup = rootView.findViewById(R.id.detail_controls_popup);
        detailControlsAddToPlaylist = rootView.findViewById(R.id.detail_controls_playlist_append);
        detailControlsDownload = rootView.findViewById(R.id.detail_controls_download);
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail);
        detailDurationView = rootView.findViewById(R.id.detail_duration_view);

        videoDescriptionRootLayout = rootView.findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = rootView.findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = rootView.findViewById(R.id.detail_description_view);
        videoDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        videoDescriptionView.setAutoLinkMask(Linkify.WEB_URLS);

        //thumbsRootLayout = rootView.findViewById(R.id.detail_thumbs_root_layout);
        thumbsUpTextView = rootView.findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = rootView.findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = rootView.findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = rootView.findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = rootView.findViewById(R.id.detail_thumbs_disabled_view);

        uploaderRootLayout = rootView.findViewById(R.id.detail_uploader_root_layout);
        uploaderTextView = rootView.findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = rootView.findViewById(R.id.detail_uploader_thumbnail_view);

        relatedStreamRootLayout = rootView.findViewById(R.id.detail_related_streams_root_layout);
        nextStreamTitle = rootView.findViewById(R.id.detail_next_stream_title);
        relatedStreamsView = rootView.findViewById(R.id.detail_related_streams_view);

        relatedStreamExpandButton = rootView.findViewById(R.id.detail_related_streams_expand);

        infoItemBuilder = new InfoItemBuilder(activity);
        setHeightThumbnail();
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        infoItemBuilder.setOnStreamSelectedListener(new OnClickGesture<StreamInfoItem>() {
            @Override
            public void selected(StreamInfoItem selectedItem) {
                setAutoplay(isAutoplayPreferred());
                if (!isAutoplayPreferred())
                    hideMainPlayer();
                selectAndLoadVideo(selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName(), null);
            }

            @Override
            public void held(StreamInfoItem selectedItem) {
                showStreamDialog(selectedItem);
            }
        });

        videoTitleRoot.setOnClickListener(this);
        uploaderRootLayout.setOnClickListener(this);
        thumbnailBackgroundButton.setOnClickListener(this);
        detailControlsBackground.setOnClickListener(this);
        detailControlsPopup.setOnClickListener(this);
        detailControlsAddToPlaylist.setOnClickListener(this);
        detailControlsDownload.setOnClickListener(this);
        relatedStreamExpandButton.setOnClickListener(this);

        detailControlsBackground.setLongClickable(true);
        detailControlsPopup.setLongClickable(true);
        detailControlsBackground.setOnLongClickListener(this);
        detailControlsPopup.setOnLongClickListener(this);
        detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());
    }

    private void showStreamDialog(final StreamInfoItem item) {
        final Context context = getContext();
        if (context == null || context.getResources() == null || getActivity() == null)
            return;

        final String[] commands = new String[]{
                context.getResources().getString(R.string.enqueue_on_background),
                context.getResources().getString(R.string.enqueue_on_popup)
        };

        final DialogInterface.OnClickListener actions = (DialogInterface dialogInterface, int i) -> {
                switch (i) {
                    case 0:
                        NavigationHelper.enqueueOnBackgroundPlayer(context, new SinglePlayQueue(item));
                        break;
                    case 1:
                        NavigationHelper.enqueueOnPopupPlayer(getActivity(), new SinglePlayQueue(item));
                        break;
                    default:
                        break;
                }
        };

        new InfoItemDialog(getActivity(), item, commands, actions).show();
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (View view, MotionEvent motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    animateView(appendControlsDetail, true, 250, 0, () ->
                            animateView(appendControlsDetail, false, 1500, 1000));
                }
                return false;
        };
    }

    private void initThumbnailViews(@NonNull StreamInfo info) {
        thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!TextUtils.isEmpty(info.getThumbnailUrl())) {
            final String infoServiceName = NewPipe.getNameOfService(info.getServiceId());
            final ImageLoadingListener onFailListener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    showSnackBarError(failReason.getCause(), UserAction.LOAD_IMAGE,
                            infoServiceName, imageUri, R.string.could_not_load_thumbnails);
                }
            };

            imageLoader.displayImage(info.getThumbnailUrl(), thumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener);
        }

        if (!TextUtils.isEmpty(info.getUploaderAvatarUrl())) {
            imageLoader.displayImage(info.getUploaderAvatarUrl(), uploaderThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }
    }

    private void initRelatedVideos(StreamInfo info) {
        if (relatedStreamsView.getChildCount() > 0)
            relatedStreamsView.removeAllViews();

        if (info.getNextVideo() != null && showRelatedStreams) {
            nextStreamTitle.setVisibility(View.VISIBLE);
            relatedStreamsView.addView(
                    infoItemBuilder.buildView(relatedStreamsView, info.getNextVideo()));
            relatedStreamsView.addView(getSeparatorView());
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
        } else
            nextStreamTitle.setVisibility(View.GONE);

        if (info.getRelatedStreams() != null
                && !info.getRelatedStreams().isEmpty() && showRelatedStreams) {
            //long first = System.nanoTime(), each;
            int to = info.getRelatedStreams().size() >= INITIAL_RELATED_VIDEOS
                    ? INITIAL_RELATED_VIDEOS
                    : info.getRelatedStreams().size();
            for (int i = 0; i < to; i++) {
                InfoItem item = info.getRelatedStreams().get(i);
                //each = System.nanoTime();
                relatedStreamsView.addView(infoItemBuilder.buildView(relatedStreamsView, item));
                //if (DEBUG) Log.d(TAG, "each took " + ((System.nanoTime() - each) / 1000000L) + "ms");
            }
            //if (DEBUG) Log.d(TAG, "Total time " + ((System.nanoTime() - first) / 1000000L) + "ms");

            relatedStreamRootLayout.setVisibility(View.VISIBLE);
            relatedStreamExpandButton.setVisibility(View.VISIBLE);

            relatedStreamExpandButton.setImageDrawable(ContextCompat.getDrawable(
                    activity, ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.expand)));
        } else {
            if (info.getNextVideo() == null)
                relatedStreamRootLayout.setVisibility(View.GONE);

            relatedStreamExpandButton.setVisibility(View.GONE);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.video_detail_menu, menu);

        updateMenuItemVisibility();

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void updateMenuItemVisibility() {

        // show kodi if set in settings
        menu.findItem(R.id.action_play_with_kodi).setVisible(
                PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                        activity.getString(R.string.show_play_with_kodi_key), false));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(isLoading.get()) {
            // if is still loading block menu
            return true;
        }

        hideMainPlayer();

        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share: {
                if(currentInfo != null) {
                    shareUrl(currentInfo.getName(), url);
                } else {
                    shareUrl(url, url);
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                openUrlInBrowser(url);
                return true;
            }
            case R.id.action_play_with_kodi:
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(
                            url.replace("https", "http")));
                } catch (Exception e) {
                    if(DEBUG) Log.i(TAG, "Failed to start kore", e);
                    showInstallKoreDialog(activity);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {});
        builder.create().show();
    }

    private void setupActionBarOnError(final String url) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandlerOnError() called with: url = [" + url + "]");
        Log.e("-----", "missing code");
    }


    private void setupActionBar(final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
        sortedStreamVideosList = new ArrayList<>(ListHelper.getSortedStreamVideosList(
                activity, info.getVideoStreams(), info.getVideoOnlyStreams(), false));

            if (oldSelectedStreamResolution == null) {
                selectedVideoStream = ListHelper.getDefaultResolutionIndex(activity, sortedStreamVideosList);
            } else {
                selectedVideoStream = ListHelper.getDefaultResolutionIndex(activity, sortedStreamVideosList, oldSelectedStreamResolution);
            }

        boolean isExternalPlayerEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false);
        spinnerToolbar.setAdapter(new SpinnerToolbarAdapter(activity, sortedStreamVideosList,
                isExternalPlayerEnabled));

        final int defaultSelection = selectedVideoStream >= sortedStreamVideosList.size() ? sortedStreamVideosList.size() - 1 : selectedVideoStream;
        selectedVideoStream = defaultSelection;

        spinnerToolbar.setSelection(defaultSelection);
        spinnerToolbar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Don't call methods twice with identical selection
                if (selectedVideoStream == position) {
                    return;
                } else {
                    selectedVideoStream = position;
                }

                VideoStream preferredVideoStream = sortedStreamVideosList.get(selectedVideoStream);

                if (playerService != null && player.getParentActivity() != null) {
                    openVideoPlayer();
                }
                oldSelectedStreamResolution = preferredVideoStream.resolution;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private LinkedList<StackItem> stack = new LinkedList<>();

    private void pushToStack(int serviceId, String videoUrl, String name, PlayQueue playQueue) {
        if (DEBUG) {
            Log.d(TAG, "pushToStack() called with: serviceId = [" + serviceId + "], videoUrl = [" + videoUrl + "], name = [" + name + "]");
        }
        if (stack.size() > 0 && stack.peek().getServiceId() == serviceId && stack.peek().getUrl().equals(videoUrl)) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = [" + serviceId + "], videoUrl == peek.getUrl = [" + videoUrl + "]");
            return;
        } else {
            Log.d(TAG, "pushToStack() wasn't equal");
        }

        stack.push(new StackItem(serviceId, videoUrl, name, playQueue));
    }

    private void setTitleToUrl(int serviceId, String videoUrl, String name) {
        if (name != null && !name.isEmpty()) {
            for (StackItem stackItem : stack) {
                if (stack.peek().getServiceId() == serviceId
                        && stackItem.getUrl().equals(videoUrl)) {
                    stackItem.setTitle(name);
                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (playerService != null && player != null && player.isInFullscreen()) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            // This will show systemUI and pause the player.
            // User can tap on Play button and video will be in fullscreen mode again
            if(!globalScreenOrientationLocked())
                player.onFullScreenButtonClicked();
            player.getPlayer().setPlayWhenReady(false);
            return true;
        }

        if (DEBUG)
            Log.d(TAG, "onBackPressed() called");

        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() <= 1) {
            // Don't stop player if user leaves fragment and if he is listening audio or watching video in popup
            if (player != null && player.videoPlayerSelected()) {
                stopService();
            }
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            return false;
        }
        // Remove top
        stack.pop();
        // Get stack item from the new top
        StackItem peek = stack.peek();
        selectStreamFromHistory(peek);

        hideMainPlayer();

        setAutoplay(false);
        selectAndLoadVideo(peek.getServiceId(), peek.getUrl(), !TextUtils.isEmpty(peek.getTitle()) ? peek.getTitle() : "", peek.getPlayQueue());
        fromBackStack = true;

        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (currentInfo != null)
            prepareAndHandleInfo(currentInfo, false);
    }

    public void selectAndLoadVideo(int serviceId, String videoUrl, String name, PlayQueue playQueue) {
        setInitialData(serviceId, videoUrl, name, playQueue);

        parallaxScrollRootView.smoothScrollTo(0, 0);
        pushToStack(serviceId, url, name, playQueue);
        startLoading(false);
        fromBackStack = false;
    }

    private void prepareAndHandleInfo(final StreamInfo info, boolean scrollToTop) {
        if (DEBUG)
            Log.d(TAG, "prepareAndHandleInfo() called with: info = [" + info + "], scrollToTop = [" + scrollToTop + "]");

        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), playQueue);
        pushToStack(serviceId, url, name, playQueue);
        showLoading();

        Log.d(TAG, "prepareAndHandleInfo() called parallaxScrollRootView.getScrollY(): "
                + parallaxScrollRootView.getScrollY());
        final boolean greaterThanThreshold = parallaxScrollRootView.getScrollY() > (int)
                (getResources().getDisplayMetrics().heightPixels * .1f);

        if (scrollToTop) parallaxScrollRootView.smoothScrollTo(0, 0);
        animateView(contentRootLayoutHiding,
                false,
                greaterThanThreshold ? 250 : 0, 0, () -> {
                handleResult(info);
                showContentWithAnimation(120, 0, .01f);
        });
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        currentInfo = null;
        if (currentWorker != null)
            currentWorker.dispose();

        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull StreamInfo result) -> {
                        isLoading.set(false);
                        showContentWithAnimation(120, 0, 0);
                        handleResult(result);
                        if (isAutoplayEnabled()) {
                            openVideoPlayer();
                        }
                    }, (@NonNull Throwable throwable) -> {
                        isLoading.set(false);
                        onError(throwable);
                });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        AudioStream audioStream = currentInfo.getAudioStreams()
                .get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 16) {
            openNormalBackgroundPlayer(append);
        } else {
            NavigationHelper.playOnExternalPlayer(activity,
                    currentInfo.getName(),
                    currentInfo.getUploaderName(),
                    audioStream);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        PlayQueue queue = setupPlayQueueForIntent(append);

        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, queue);
        } else {
            NavigationHelper.playOnPopupPlayer(activity, queue);
        }
    }

    private void openVideoPlayer() {
        VideoStream selectedVideoStream = getSelectedVideoStream();

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            NavigationHelper.playOnExternalPlayer(activity,
                    currentInfo.getName(),
                    currentInfo.getUploaderName(),
                    selectedVideoStream);
        } else {
            setupMainPlayer();
        }
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        PlayQueue queue = setupPlayQueueForIntent(append);

        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, queue);
        } else {
            NavigationHelper.playOnBackgroundPlayer(activity, queue);
        }
    }

    private void setupMainPlayer() {
        boolean useOldPlayer = PlayerHelper.isUsingOldPlayer(activity) || (Build.VERSION.SDK_INT < 16);

        if (!useOldPlayer) {
            if (playerService == null) {
                startService();
                shouldOpenPlayerAfterServiceConnects = true;
                return;
            }
            if (currentInfo == null)
                return;

            PlayQueue queue = setupPlayQueueForIntent(false);

            addVideoPlayerView();
            playerService.getView().setVisibility(View.GONE);

            if (player != null && player.getPlayer() != null)
                player.getPlayer().clearVideoSurface();

            Intent playerIntent = NavigationHelper.getPlayerIntent(getContext(), MainPlayerService.class, queue, getSelectedVideoStream().getResolution());
            activity.startService(playerIntent);
        } else {
            // Internal Player
            Intent mIntent = new Intent(activity, PlayVideoActivity.class)
                    .putExtra(PlayVideoActivity.VIDEO_TITLE, currentInfo.getName())
                    .putExtra(PlayVideoActivity.STREAM_URL, getSelectedVideoStream().getUrl())
                    .putExtra(PlayVideoActivity.VIDEO_URL, currentInfo.getUrl())
                    .putExtra(PlayVideoActivity.START_POSITION, currentInfo.getStartPosition());
            startActivity(mIntent);
        }
    }

    private void hideMainPlayer() {
        if (playerService == null || playerService.getView() == null || !player.videoPlayerSelected())
            return;

        removeVideoPlayerView();
        playerService.stop();
        playerService.getView().setVisibility(View.GONE);
    }

    private PlayQueue setupPlayQueueForIntent(boolean append) {
        if (fromBackStack) {
            // We got PlayQueue from StackItem. It was already setup.
            fromBackStack = false;
            return playQueue;
        } else if (append) {
            return new SinglePlayQueue(currentInfo);
        }

        PlayQueue queue;
        // Size can be 0 because queue removes bad stream automatically when error occurs
        // For now we can just select another quality and recreate SinglePlayQueue here
        // Solution will be a reimplementation of stream selector
        if (playQueue == null || playQueue.size() == 0)
            queue = new SinglePlayQueue(currentInfo);
        else
            queue = playQueue;

        // Continue from paused position
        long currentPosition = 0;

        // The same playQueue is in the player now. It contains current item and playback position
        if (player != null && player.getPlayQueue() != null && queue.TAG.equals(player.getPlayQueue().TAG)) {
            player.setRecovery();
            queue = player.getPlayQueue();
            currentPosition = queue.getItem().getRecoveryPosition();
        }
        // We use it to continue playback when returning back from popup or audio players
        else if (queue.getItem() != null && queue.getItem().getRecoveryPosition() > 0) {
            currentPosition = queue.getItem().getRecoveryPosition();
        }
        // We use it to continue playback when quality was changed
        else if (player != null && player.getVideoUrl() != null && player.getVideoUrl().equals(url)) {
            currentPosition = playerService.getPlaybackPosition();
        }

        queue.setRecovery(queue.getIndex(), currentPosition);

        return queue;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/
    /**
     * Prior to Kitkat, hiding system ui causes the player view to be overlaid and require two
     * clicks to get rid of that invisible overlay. By showing the system UI on actions/events,
     * that overlay is removed and the player view is put to the foreground.
     *
     * Post Kitkat, navbar and status bar can be pulled out by swiping the edge of
     * screen, therefore, we can do nothing or hide the UI on actions/events.
     * */
    private void changeSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            showSystemUi();
        } else {
            hideSystemUi();
        }
    }

    public void setAutoplay(boolean autoplay) {
        autoPlayEnabled = autoplay;
    }

    // This method asks preferences for user selected behaviour
    private boolean isAutoplayPreferred() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean(getContext().getString(R.string.autoplay_always_key), false);
    }

    // This method overrides default behaviour when setAutoplay() is called
    private boolean isAutoplayEnabled() {
        if (playQueue != null && playQueue.getStreams().size() == 0)
            return false;
        else
            return autoPlayEnabled;
    }

    private void addVideoPlayerView() {
        ViewGroup viewHolder = getView().findViewById(
                player.isInFullscreen() ?
                        R.id.video_item_detail :
                        R.id.detail_thumbnail_root_layout);

        // Prevent from re-adding a view multiple times
        if(playerService.getView().getParent() != null && playerService.getView().getParent().hashCode() == viewHolder.hashCode())
                return;

        removeVideoPlayerView();
        viewHolder.addView(playerService.getView());
    }

    private void removeVideoPlayerView() {
        playerService.removeViewFromParent();
    }

    private VideoStream getSelectedVideoStream() {
        return sortedStreamVideosList.get(selectedVideoStream);
    }

    private void prepareDescription(final String descriptionHtml) {
        if (TextUtils.isEmpty(descriptionHtml)) {
            return;
        }

        disposables.add(Single.just(descriptionHtml)
                .map((@io.reactivex.annotations.NonNull String description) -> {
                        Spanned parsedDescription;
                        if (Build.VERSION.SDK_INT >= 24) {
                            parsedDescription = Html.fromHtml(description, 0);
                        } else {
                            //noinspection deprecation
                            parsedDescription = Html.fromHtml(description);
                        }
                        return parsedDescription;
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@io.reactivex.annotations.NonNull Spanned spanned) -> {
                        videoDescriptionView.setText(spanned);
                        videoDescriptionView.setVisibility(View.VISIBLE);
                }));
    }

    private View getSeparatorView() {
        View separator = new View(activity);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        int m8 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        int m5 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        params.setMargins(m8, m5, m8, m5);
        separator.setLayoutParams(params);

        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(R.attr.separator_color, typedValue, true);
        separator.setBackgroundColor(typedValue.data);

        return separator;
    }

    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        int height = isPortrait
                ? (int) (metrics.widthPixels / (16.0f / 9.0f))
                : (int) (metrics.heightPixels / 2f);
        thumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
    }

    private void showContentWithAnimation(long duration,
                                          long delay,
                                          @FloatRange(from = 0.0f, to = 1.0f) float translationPercent) {
        int translationY = (int) (getResources().getDisplayMetrics().heightPixels *
                (translationPercent > 0.0f ? translationPercent : .06f));

        contentRootLayoutHiding.animate().setListener(null).cancel();
        contentRootLayoutHiding.setAlpha(0f);
        contentRootLayoutHiding.setTranslationY(translationY);
        contentRootLayoutHiding.setVisibility(View.VISIBLE);
        contentRootLayoutHiding.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(delay)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();

        uploaderRootLayout.animate().setListener(null).cancel();
        uploaderRootLayout.setAlpha(0f);
        uploaderRootLayout.setTranslationY(translationY);
        uploaderRootLayout.setVisibility(View.VISIBLE);
        uploaderRootLayout.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay((long) (duration * .5f) + delay)
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();

        if (showRelatedStreams) {
            relatedStreamRootLayout.animate().setListener(null).cancel();
            relatedStreamRootLayout.setAlpha(0f);
            relatedStreamRootLayout.setTranslationY(translationY);
            relatedStreamRootLayout.setVisibility(View.VISIBLE);
            relatedStreamRootLayout.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay((long) (duration * .8f) + delay)
                    .setDuration(duration)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }
    }

    private void setInitialData(int serviceId, String url, String name, PlayQueue playQueue) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(name) ? name : "";
        this.playQueue = playQueue;
    }

    private void setErrorImage(final int imageResource) {
        if (thumbnailImageView == null || activity == null)
            return;

        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, imageResource));
        animateView(thumbnailImageView, false, 0, 0,
                () -> animateView(thumbnailImageView, true, 500));
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        showError(message, showRetryButton, R.drawable.not_available_monkey);
    }

    private void showError(String message, boolean showRetryButton, @DrawableRes int imageError) {
        super.showError(message, showRetryButton);
        setErrorImage(imageError);
    }

    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                hideMainPlayer();
            }
        };
        IntentFilter intentFilter = new IntentFilter(ACTION_HIDE_MAIN_PLAYER);
        getActivity().registerReceiver(broadcastReceiver, intentFilter);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    //////////////////////////////////////////////////////////////////////////*/

    private boolean globalScreenOrientationLocked() {
        // 1: Screen orientation changes using accelerometer
        // 0: Screen orientation is locked
        return !(android.provider.Settings.System.getInt(getActivity().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
    }

    private void setupOrientation() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int newOrientation;
        if (globalScreenOrientationLocked()) {
            boolean lastOrientationWasLandscape
                    = sharedPreferences.getBoolean(getString(R.string.last_orientation_landscape_key), false);
            newOrientation = lastOrientationWasLandscape
                                            ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else
            newOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

        if (newOrientation != getActivity().getRequestedOrientation())
            getActivity().setRequestedOrientation(newOrientation);
    }

    @Override
    public void onSettingsChanged() {
        if (player == null)
            return;

        if(!globalScreenOrientationLocked())
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        player.setupScreenRotationButton(globalScreenOrientationLocked());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();

        animateView(contentRootLayoutHiding, false, 200);
        animateView(spinnerToolbar, false, 200);
        animateView(thumbnailPlayButton, false, 50);
        animateView(detailDurationView, false, 100);

        videoTitleTextView.setText(name != null ? name : "");
        videoTitleTextView.setMaxLines(1);
        animateView(videoTitleTextView, true, 0);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoTitleToggleArrow.setVisibility(View.GONE);
        videoTitleRoot.setClickable(false);

        imageLoader.cancelDisplayTask(thumbnailImageView);
        imageLoader.cancelDisplayTask(uploaderThumb);
        thumbnailImageView.setImageBitmap(null);
        uploaderThumb.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull StreamInfo info) {
        super.handleResult(info);

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), playQueue);
        pushToStack(serviceId, url, name, playQueue);

        animateView(thumbnailPlayButton, true, 200);
        videoTitleTextView.setText(name);

        if (!TextUtils.isEmpty(info.getUploaderName())) {
            uploaderTextView.setText(info.getUploaderName());
            uploaderTextView.setVisibility(View.VISIBLE);
            uploaderTextView.setSelected(true);
        } else {
            uploaderTextView.setVisibility(View.GONE);
        }
        uploaderThumb.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));

        if (info.getViewCount() >= 0) {
            videoCountView.setText(Localization.localizeViewCount(activity, info.getViewCount()));
            videoCountView.setVisibility(View.VISIBLE);
        } else {
            videoCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() >= 0) {
                thumbsDownTextView.setText(Localization.shortCount(activity, info.getDislikeCount()));
                thumbsDownTextView.setVisibility(View.VISIBLE);
                thumbsDownImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsDownTextView.setVisibility(View.GONE);
                thumbsDownImageView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                thumbsUpTextView.setText(Localization.shortCount(activity, info.getLikeCount()));
                thumbsUpTextView.setVisibility(View.VISIBLE);
                thumbsUpImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsUpTextView.setVisibility(View.GONE);
                thumbsUpImageView.setVisibility(View.GONE);
            }
            thumbsDisabledTextView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            detailDurationView.setBackgroundColor(ContextCompat.getColor(activity, R.color.duration_background_color));
            animateView(detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            detailDurationView.setText(R.string.duration_live);
            detailDurationView.setBackgroundColor(ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animateView(detailDurationView, true, 100);
        } else {
            detailDurationView.setVisibility(View.GONE);
        }

        videoTitleRoot.setClickable(true);
        videoTitleToggleArrow.setVisibility(View.VISIBLE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoDescriptionView.setVisibility(View.GONE);
        videoDescriptionRootLayout.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(info.getUploadDate())) {
            videoUploadDateView.setText(Localization.localizeDate(activity, info.getUploadDate()));
        }
        prepareDescription(info.getDescription());

        animateView(spinnerToolbar, true, 500);
        setupActionBar(info);
        initThumbnailViews(info);
        initRelatedVideos(info);
        if (wasRelatedStreamsExpanded) {
            toggleExpandRelatedVideos(currentInfo);
            wasRelatedStreamsExpanded = false;
        }
        setTitleToUrl(info.getServiceId(), info.getUrl(), info.getName());

        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(),
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.getServiceId()),
                    info.getUrl(),
                    0);
        }

        switch (info.getStreamType()) {
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                detailControlsDownload.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                break;
            default:
                if (!info.getVideoStreams().isEmpty()
                        || !info.getVideoOnlyStreams().isEmpty()) break;

            detailControlsBackground.setVisibility(View.GONE);
            detailControlsPopup.setVisibility(View.GONE);
            spinnerToolbar.setVisibility(View.GONE);
            thumbnailPlayButton.setImageResource(R.drawable.ic_headset_white_24dp);
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception))
            return true;

        if (exception instanceof YoutubeStreamExtractor.GemaException) {
            onBlockedByGemaError();
        } else if (exception instanceof ContentNotAvailableException) {
            showError(getString(R.string.content_not_available), false);

            playNextStream();
        } else {
            int errorId = exception instanceof YoutubeStreamExtractor.DecryptException
                    ? R.string.youtube_signature_decryption_error
                    : exception instanceof ParsingException
                    ? R.string.parsing_error
                    : R.string.general_error;
            onUnrecoverableError(exception,
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(serviceId),
                    url,
                    errorId);
        }

        return true;
    }

    public void onBlockedByGemaError() {
        thumbnailBackgroundButton.setOnClickListener((View v) -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.c3s_url)));
                startActivity(intent);
        });

        showError(getString(R.string.blocked_by_gema), false, R.drawable.gruese_die_gema);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPlaybackUpdate(int state, int repeatMode, boolean shuffled, PlaybackParameters parameters) {
        if (state == BasePlayer.STATE_COMPLETED) {
            if (player.isInFullscreen())
                player.onFullScreenButtonClicked();

            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else if (state == BasePlayer.STATE_PLAYING) {
            setupOrientation();
        }
    }

    @Override
    public void onProgressUpdate(int currentProgress, int duration, int bufferPercent) {
        // We don't want to interrupt playback and don't want to see notification if player is stopped
        if (player == null || player.getPlayer() == null || !player.isPlaying() || player.audioPlayerSelected())
            return;

        // This will be called when user goes to another app
        if (isPaused) {
            // Video enabled. Let's think what to do with source in background
            if (player.backgroundPlaybackEnabledInSettings())
                player.useVideoSource(false);
            else
                player.getPlayer().setPlayWhenReady(false);
        } else
            player.useVideoSource(true);
    }

    @Override
    public void onMetadataUpdate(StreamInfo info) {
        // We don't want to update interface twice
        if (playQueue == null || playQueue instanceof SinglePlayQueue || currentInfo == null || currentInfo.getId().equals(info.getId()) || player.getParentActivity() == null)
            return;

        currentInfo = info;
        setAutoplay(false);
        prepareAndHandleInfo(info, true);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (error.type == ExoPlaybackException.TYPE_SOURCE || error.type == ExoPlaybackException.TYPE_UNEXPECTED) {
            hideMainPlayer();
            if (playerService != null && player.isInFullscreen())
                player.onFullScreenButtonClicked();

            playNextStream();
        }
    }

    @Override
    public void onServiceStopped() {
        unbind();
    }

    @Override
    public void onFullScreenButtonClicked(boolean fullscreen) {
        if (playerService.getView() == null || player.getParentActivity() == null)
            return;

        View view = playerService.getView();
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null)
            return;

        if (fullscreen) {
            hideSystemUi();
            hideActionBar();
        } else {
            showSystemUi();
            showActionBar();
        }

        addVideoPlayerView();
    }

    @Override
    public boolean isPaused() {
        return isPaused;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    //////////////////////////////////////////////////////////////////////////*/


    private void showSystemUi() {
        if (DEBUG)
            Log.d(TAG, "showSystemUi() called");

        if (player == null || getActivity() == null)
            return;

        getActivity().getWindow().getDecorView().setSystemUiVisibility(0);
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
}

    private void showActionBar() {
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            TypedArray a = getActivity().getTheme().obtainStyledAttributes(R.style.ThemeOverlay_AppCompat_ActionBar, new int[]{R.attr.actionBarSize});
            int attributeResourceId = a.getResourceId(0, 0);
            params.setMargins(0, (int) getResources().getDimension(attributeResourceId), 0, 0);
            getActivity().findViewById(R.id.fragment_holder).setLayoutParams(params);
        }
    }

    private void hideSystemUi() {
        if (playerService == null || !player.isInFullscreen() || getActivity() == null)
            return;
        if (DEBUG)
            Log.d(TAG, "hideSystemUi() called");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            getActivity().getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

    }

    private void hideActionBar() {
        if (player == null || !player.isInFullscreen() || getView() == null)
            return;

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMargins(0, 0, 0, 0);
            getActivity().findViewById(R.id.fragment_holder).setLayoutParams(params);
        }
    }

    // Listener implementation
    public void hideSystemUIIfNeeded() {
        hideSystemUi();
    }

    private void setupBrightness(boolean save) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
        float brightnessLevel;

        if (save) {
            // Save current brightness level
            brightnessLevel = lp.screenBrightness;
            sp.edit().putFloat(getString(R.string.brightness_level_key), brightnessLevel).apply();

            // Restore the old  brightness when fragment.onPause() called.
            // It means when user leaves this fragment brightness will be set to system brightness
            lp.screenBrightness = -1;
        } else {
            // Restore already saved brightness level
            brightnessLevel = sp.getFloat(getString(R.string.brightness_level_key), lp.screenBrightness);
            if (brightnessLevel <= 0.0f && brightnessLevel > 1.0f)
                return;

            lp.screenBrightness = brightnessLevel;
        }
        getActivity().getWindow().setAttributes(lp);
    }

    private void playNextStream() {
        if (playQueue != null && playQueue.getStreams().size() > playQueue.getIndex() + 1) {
            playQueue.setIndex(playQueue.getIndex() + 1);
            PlayQueueItem next = playQueue.getItem();
            setAutoplay(true);
            selectAndLoadVideo(next.getServiceId(), next.getUrl(), next.getTitle(), playQueue);
        }

    }

    private void selectStreamFromHistory(StackItem peek) {
        // Choose stream from saved to history item. If we will not select it we will play the wrong stream
        PlayQueue peekQueue = peek.getPlayQueue();
        if (peekQueue != null && (peekQueue instanceof PlaylistPlayQueue || peekQueue instanceof ChannelPlayQueue)) {
            int index = -1;
            for (int i = 0; i < peekQueue.getStreams().size(); i++) {
                PlayQueueItem item = peekQueue.getItem(i);
                // Trying to find an URL in PlayQueue that equals an URL from StackItem
                if (item.getUrl().equals(peek.getUrl())) {
                    index = i;
                    break;
                }
            }
            if (index != -1)
                peekQueue.setIndex(index);
        }
    }

    private void checkLandscape() {
        if ((!player.isPlaying() && player.getPlayQueue() != playQueue) || player.getPlayQueue() == null)
            setAutoplay(true);

        // Let's give a user time to look at video information page if video is not playing
        if (player.isPlaying()) {
            player.selectAudioPlayer(false);
            player.checkLandscape();
        }
    }
}