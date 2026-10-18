package com.proxy.wireopen;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.widget.NestedScrollView;
import com.proxy.wireopen.adapter.RoutingAppAdapter;
import com.proxy.wireopen.model.AppItem;
import com.proxy.wireopen.model.ConnectionState;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.model.IpPureResult;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;
import com.proxy.wireopen.repository.ProfileRepository;
import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.PresetRegion;
import com.proxy.wireopen.repository.PresetRegionManager;
import com.proxy.wireopen.service.DoubleHopConfigBuilder;
import com.proxy.wireopen.service.DoubleHopVpnService;
import com.proxy.wireopen.service.UnifiedVpnService;
import com.proxy.wireopen.service.IpPureChecker;
import com.proxy.wireopen.util.CredentialManager;
import com.proxy.wireopen.util.IpGeoResolver;
import com.proxy.wireopen.engine.OpenVpnEngine;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.PortSpeedTester;
import com.proxy.wireopen.util.RegionDetector;
import com.proxy.wireopen.util.SplitTunnelManager;
import com.proxy.wireopen.util.TrafficStatsManager;
import com.proxy.wireopen.view.OscilloscopeWaveformView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import android.content.res.ColorStateList;
import java.text.Collator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class MainActivity extends AppCompatActivity {

    private ProfileRepository repository;
    private RoutingAppAdapter routingAppAdapter;

    // 4 Major Tab Pages (Dashboard, Nodes, Routing, IP Pureness)
    private View pageDashboard;
    private View pageNodes;
    private View pageRouting;
    private View pageIppure;

    // Bottom Navigation Elements (4 Tabs)
    private LinearLayout navItemDashboard;
    private LinearLayout navItemNodes;
    private LinearLayout navItemRouting;
    private LinearLayout navItemIppure;

    private LinearLayout navPillDashboard;
    private LinearLayout navPillNodes;
    private LinearLayout navPillRouting;
    private LinearLayout navPillIppure;

    private TextView navLabelDashboard;
    private TextView navLabelNodes;
    private TextView navLabelRouting;
    private TextView navLabelIppure;

    // Top Bar Actions
    private MaterialButton btnOpenLogs;
    private MaterialButton btnThemeToggle;
    private MaterialButton btnOpenSettings;

    // Independent Log Console references
    private BottomSheetDialog logConsoleDialog = null;
    private TextView tvBottomSheetLogs = null;
    private NestedScrollView scrollBottomSheetLogs = null;
    private LogManager.LogListener logConsoleListener = null;

    // Giant Orb Switch Views (Dashboard)
    private View orbRing;
    private LinearLayout btnOrbSwitch;
    private TextView tvOrbIcon;
    private TextView tvOrbStatusText;
    private TextView tvProtectionBadge;

    // Mini Outbound Banner
    private TextView tvMiniGeoFlag;
    private TextView tvMiniGeoIp;
    private TextView tvMiniProtocolBadge;
    private TextView tvMiniGeoLocation;
    private TextView tvMiniPingBadge;

    // Throughput & Oscilloscope
    private TextView tvPeakSpeed;
    private TextView tvSpeedDown;
    private TextView tvSpeedDownUnit;
    private TextView tvTotalDown;
    private TextView tvSpeedUp;
    private TextView tvSpeedUpUnit;
    private TextView tvTotalUp;
    private OscilloscopeWaveformView waveformView;

    // Nodes Page Views (Redesigned)
    private View cardEmptyState;
    private final Map<String, List<TextView>> portBadgeViews = new HashMap<>();
    private final Map<String, List<View>> portCardViews = new HashMap<>();

    private LinearLayout layoutNodesContainer;
    private EditText etSearchNodes;
    private View btnNodeImport;
    private MaterialButton btnBatchImport;
    private MaterialButton btnFilterWg;
    private MaterialButton btnFilterOvpn;
    private MaterialButton btnTestAllSpeeds;
    private MaterialButton btnNodePortSelect;
    private MaterialButton btnToggleAllGroups;
    private MaterialButton btnSortSpeedometer;
    private int currentSelectedPort = 443;
    private boolean isPortManuallyOverridden = false;
    private boolean sortByLatency = false;
    private boolean allGroupsExpanded = true;
    private boolean favoriteDrawerExpanded = true;
    private final Set<String> collapsedCountries = new HashSet<>();

    // Favorites SharedPreferences
    private SharedPreferences favoritesPrefs;
    private final Set<String> favoriteNodeIds = new HashSet<>();
    private static final String PREF_FAVORITES = "user_favorites";
    private static final String KEY_FAVORITE_SET = "favorite_unique_ids";

    // Egress probe state
    private String egressProbeStatus = null;
    private final java.util.concurrent.atomic.AtomicBoolean isProbingEgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    private String activeProtocol = "WIREGUARD";
    private String searchQuery = "";

    // Routing Page Views
    private TextView tvRoutingSummary;
    private MaterialButton btnModeGlobal;
    private MaterialButton btnModeBypass;
    private MaterialButton btnModeAllow;
    private TextView tvModeDesc;
    private EditText etRoutingSearch;
    private CheckBox cbFilterSystemApps;
    private ProgressBar pbRoutingLoading;
    private RecyclerView rvRoutingApps;
    private boolean appsLoaded = false;

    // IP Pureness WebView Page Views
    private ProgressBar pbIppureWeb;
    private SwipeRefreshLayout swipeRefreshIppure;
    private WebView webviewIppure;
    private boolean ippureWebLoaded = false;

    // Launchers
    private ActivityResultLauncher<Intent> vpnPrepareLauncher;
    private ActivityResultLauncher<Intent> doubleHopPrepareLauncher;
    private String pendingDoubleHopConfig;
    private ProxyProfile pendingDoubleHopProfile;
    private String pendingDoubleHopFirstName;
    private String pendingDoubleHopSecondName;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> batchFilePickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Timer & Speed State
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long connectionStartTime = 0;
    private boolean isTimerRunning = false;

    private long lastRxBytes = 0;
    private long lastTxBytes = 0;
    private long lastTrafficTimestamp = 0;
    private double peakSpeedBytesPerSec = 0;
    private double currentDownSpeed = 0;
    private double currentUpSpeed = 0;
    private ConnectionState lastConnectionState = null;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRenderRunnable = this::renderNodeCards;
    private final Handler latencyUiHandler = new Handler(Looper.getMainLooper());
    private final Set<String> pendingLatencyUiUpdates = ConcurrentHashMap.newKeySet();
    private final Runnable latencyUiRunnable = () -> {
        for (String uniqueId : pendingLatencyUiUpdates) {
            PresetPortItem item = PresetRegionManager.getInstance().findPortByUniqueId(uniqueId);
            if (item == null) continue;
            List<TextView> badges = portBadgeViews.get(uniqueId);
            List<View> cards = portCardViews.get(uniqueId);
            if (badges != null && cards != null) {
                for (int i = 0; i < cards.size(); i++) {
                    View card = cards.get(i);
                    TextView badge = i < badges.size() ? badges.get(i) : null;
                    if (card != null && badge != null) {
                        View btnPing = card.findViewById(R.id.btn_node_ping);
                        View dotLatency = card.findViewById(R.id.dot_node_latency);
                        if (btnPing != null && dotLatency != null) {
                            updateNodeLatencyDisplay(item, btnPing, dotLatency, badge);
                        }
                    }
                }
            }
        }
        pendingLatencyUiUpdates.clear();
        updateActiveProfileUi();
    };

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                updateProtectionBadgeText();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private void updateProtectionBadgeText() {
        if (!isTimerRunning) return;
        TrafficStatsManager stats = TrafficStatsManager.getInstance();
        String timeStr;
        if (stats != null && stats.isSessionActive()) {
            timeStr = stats.getSessionDurationFormatted();
        } else {
            long elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
            long h = elapsed / 3600;
            long m = (elapsed % 3600) / 60;
            long s = elapsed % 60;
            timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        if (egressProbeStatus != null) {
            tvProtectionBadge.setText(egressProbeStatus + " · " + timeStr);
            if (egressProbeStatus.startsWith("🟢")) {
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            } else if (egressProbeStatus.startsWith("🔴")) {
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red));
            } else {
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            }
        } else {
            tvProtectionBadge.setText("🛡️ 已受安全代理保护 · " + timeStr);
            tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        }
    }

        private int request204(String endpoint, int timeoutMs) throws IOException {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "WireOpenProxy/1.2.9 (Android)");
            return connection.getResponseCode();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readHttpText(String endpoint, int timeoutMs) throws IOException {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", "WireOpenProxy/1.2.9 (Android)");
            int code = connection.getResponseCode();
            if (code != java.net.HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void probeEgressConnectivity() {
        if (!isProbingEgress.compareAndSet(false, true)) return;
        egressProbeStatus = "🟡 探测外网中";
        updateProtectionBadgeText();

        new Thread(() -> {
            boolean isReachable = false;
            long latencyMs = -1L;
            String publicIp = null;
            long probeStarted = System.currentTimeMillis();
            try {
                int code = request204("https://cp.cloudflare.com/generate_204", 4000);
                if (code == 204 || code == 200) {
                    isReachable = true;
                    latencyMs = Math.max(1L, System.currentTimeMillis() - probeStarted);
                }
            } catch (Exception ignored) {
                // Try another HTTPS endpoint below.
            }
            if (!isReachable) {
                probeStarted = System.currentTimeMillis();
                try {
                    int code = request204("https://www.gstatic.com/generate_204", 3500);
                    if (code == 204 || code == 200) {
                        isReachable = true;
                        latencyMs = Math.max(1L, System.currentTimeMillis() - probeStarted);
                    }
                } catch (Exception ignored) {
                    // Both HTTPS probes failed.
                }
            }
            if (isReachable) {
                try {
                    publicIp = readHttpText("https://api.ipify.org", 3500);
                } catch (Exception firstError) {
                    try {
                        publicIp = readHttpText("https://icanhazip.com", 3000);
                    } catch (Exception ignored) {
                        // Connectivity works, but the IP lookup failed.
                    }
                }
            }

            final boolean reachable = isReachable;
            final long latency = latencyMs;
            final String ip = publicIp;
            runOnUiThread(() -> {
                isProbingEgress.set(false);
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())
                        || UnifiedVpnService.getCurrentState() != ConnectionState.CONNECTED) {
                    return;
                }
                if (reachable) {
                    if (ip != null && !ip.trim().isEmpty()) {
                        egressProbeStatus = "🟢 外网已通 · " + ip.trim()
                                + " (" + (latency > 0 ? latency + "ms" : "延迟未知") + ")";
                    } else {
                        egressProbeStatus = "🟢 外网已通 (出口 IP 查询失败)";
                    }
                } else {
                    egressProbeStatus = "🔴 外网探测失败";
                }
                updateProtectionBadgeText();
                LogManager.info("MainActivity", "外网出口探测: " + egressProbeStatus);
            });
        }, "WireOpen-EgressProbe").start();
    }

    private String lastErrorMessageDisplay = null;

    private final BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (UnifiedVpnService.ACTION_STATE_CHANGED.equals(action)) {
                String stateName = intent.getStringExtra(UnifiedVpnService.EXTRA_STATE);
                String errorMessage = intent.getStringExtra(UnifiedVpnService.EXTRA_ERROR_MESSAGE);
                if (stateName != null) {
                    try {
                        ConnectionState state = ConnectionState.valueOf(stateName);
                        updateConnectionUi(state, errorMessage);
                    } catch (IllegalArgumentException ignored) {}
                }
            } else if (UnifiedVpnService.ACTION_TRAFFIC_UPDATE.equals(action)) {
                long rx = intent.getLongExtra(UnifiedVpnService.EXTRA_RX_BYTES, -1);
                long tx = intent.getLongExtra(UnifiedVpnService.EXTRA_TX_BYTES, -1);
                if (rx >= 0 && tx >= 0) {
                    onTrafficUpdate(rx, tx);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 16 edge-to-edge system bars insets handling
        View rootView = findViewById(R.id.main_layout);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, 0);
                return insets;
            });
        }

        CredentialManager.init(this);
        TrafficStatsManager.init(this);
        repository = new ProfileRepository(this);
        removeBuiltInDemoProfiles();
        repository.loadBundledProfilesIfEmpty(this);
        normalizeImportedProfileNames();

        favoritesPrefs = getSharedPreferences(PREF_FAVORITES, Context.MODE_PRIVATE);
        favoriteNodeIds.clear();
        Set<String> savedFavs = favoritesPrefs.getStringSet(KEY_FAVORITE_SET, null);
        if (savedFavs != null) {
            favoriteNodeIds.addAll(savedFavs);
        }

        initViews();
        setupNavigation();
        setupLaunchers();
        setupPresetRegions();
        setupRoutingPage();
        setupIppureWebView();
        checkPermissions();

        handleIncomingIntent(getIntent());

        updateActiveProfileUi();
        updateConnectionUi(UnifiedVpnService.getCurrentState());
    }

    private void initViews() {
        // 4 Major Tab Pages (Dashboard, Nodes, Routing, IP Pureness)
        pageDashboard = findViewById(R.id.page_dashboard);
        pageNodes = findViewById(R.id.page_nodes);
        pageRouting = findViewById(R.id.page_routing);
        pageIppure = findViewById(R.id.page_ippure);

        // 4 Nav Items
        navItemDashboard = findViewById(R.id.nav_item_dashboard);
        navItemNodes = findViewById(R.id.nav_item_nodes);
        navItemRouting = findViewById(R.id.nav_item_routing);
        navItemIppure = findViewById(R.id.nav_item_ippure);

        navPillDashboard = findViewById(R.id.nav_pill_dashboard);
        navPillNodes = findViewById(R.id.nav_pill_nodes);
        navPillRouting = findViewById(R.id.nav_pill_routing);
        navPillIppure = findViewById(R.id.nav_pill_ippure);

        navLabelDashboard = findViewById(R.id.nav_label_dashboard);
        navLabelNodes = findViewById(R.id.nav_label_nodes);
        navLabelRouting = findViewById(R.id.nav_label_routing);
        navLabelIppure = findViewById(R.id.nav_label_ippure);

        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        if (tvAppVersion != null) {
            try {
                String version = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                tvAppVersion.setText(version != null ? "v" + version : "");
            } catch (Exception ignored) {
                tvAppVersion.setText("");
            }
        }

        // Top Bar (📜 Log | 🌙 Theme | ⚙️ Settings)
        btnOpenLogs = findViewById(R.id.btn_open_logs);
        if (btnOpenLogs != null) {
            btnOpenLogs.setOnClickListener(v -> showLogConsoleBottomSheet());
        }

        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        setupThemeToggle();

        MaterialButton btnDoubleHop = findViewById(R.id.btn_double_hop);
        if (btnDoubleHop != null) {
            btnDoubleHop.setOnClickListener(v -> showDoubleHopDialog());
        }

        btnOpenSettings = findViewById(R.id.btn_open_settings);
        if (btnOpenSettings != null) {
            btnOpenSettings.setOnClickListener(v -> showSettingsBottomSheet());
        }

        // Giant Orb Switch Views (Dashboard)
        orbRing = findViewById(R.id.orb_ring);
        btnOrbSwitch = findViewById(R.id.btn_orb_switch);
        tvOrbIcon = findViewById(R.id.tv_orb_icon);
        tvOrbStatusText = findViewById(R.id.tv_orb_status_text);
        tvProtectionBadge = findViewById(R.id.tv_protection_badge);

        btnOrbSwitch.setOnClickListener(v -> toggleVpnConnection());

        // Mini Outbound Info
        tvMiniGeoFlag = findViewById(R.id.tv_mini_geo_flag);
        tvMiniGeoIp = findViewById(R.id.tv_mini_geo_ip);
        tvMiniProtocolBadge = findViewById(R.id.tv_mini_protocol_badge);
        tvMiniGeoLocation = findViewById(R.id.tv_mini_geo_location);
        tvMiniPingBadge = findViewById(R.id.tv_mini_ping_badge);

        // Throughput & Oscilloscope
        tvPeakSpeed = findViewById(R.id.tv_peak_speed);
        tvSpeedDown = findViewById(R.id.tv_speed_down);
        tvSpeedDownUnit = findViewById(R.id.tv_speed_down_unit);
        tvTotalDown = findViewById(R.id.tv_total_down);
        tvSpeedUp = findViewById(R.id.tv_speed_up);
        tvSpeedUpUnit = findViewById(R.id.tv_speed_up_unit);
        tvTotalUp = findViewById(R.id.tv_total_up);
        waveformView = findViewById(R.id.waveform_view);

        // Nodes Actions & Views (Redesigned)
        cardEmptyState = findViewById(R.id.card_empty_state);
        layoutNodesContainer = findViewById(R.id.layout_nodes_container);
        etSearchNodes = findViewById(R.id.et_search_nodes);
        btnNodeImport = findViewById(R.id.btn_node_import);
        btnBatchImport = findViewById(R.id.btn_batch_import);
        btnFilterWg = findViewById(R.id.btn_filter_wg);
        btnFilterOvpn = findViewById(R.id.btn_filter_ovpn);
        btnTestAllSpeeds = findViewById(R.id.btn_test_all_speeds);
        btnNodePortSelect = findViewById(R.id.btn_node_port_select);
        btnToggleAllGroups = findViewById(R.id.btn_toggle_all_groups);
        btnSortSpeedometer = findViewById(R.id.btn_sort_speedometer);

        View btnEmptyImport = findViewById(R.id.btn_empty_import);
        if (btnEmptyImport != null) {
            btnEmptyImport.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        }

        if (btnTestAllSpeeds != null) {
            btnTestAllSpeeds.setOnClickListener(v -> testAllPortsSpeed());
        }

        if (btnNodePortSelect != null) {
            btnNodePortSelect.setText(currentSelectedPort + " ▾");
            btnNodePortSelect.setOnClickListener(v -> showPortSelectionDialog());
        }

        if (btnToggleAllGroups != null) {
            btnToggleAllGroups.setText(allGroupsExpanded ? "全部折叠" : "全部展开");
            btnToggleAllGroups.setOnClickListener(v -> toggleAllGroups());
        }

        if (btnSortSpeedometer != null) {
            updateSortSpeedometerUi();
            btnSortSpeedometer.setOnClickListener(v -> toggleSortMode());
        }

        if (btnNodeImport != null) {
            btnNodeImport.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        }

        if (btnBatchImport != null) {
            btnBatchImport.setOnClickListener(v -> batchFilePickerLauncher.launch("*/*"));
        }

        if (etSearchNodes != null) {
            etSearchNodes.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                    if (!searchQuery.isEmpty()) {
                        collapsedCountries.clear();
                    }
                    searchHandler.removeCallbacks(searchRenderRunnable);
                    searchHandler.postDelayed(searchRenderRunnable, 120);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnFilterWg != null) {
            btnFilterWg.setOnClickListener(v -> selectProtocol("WIREGUARD"));
        }
        if (btnFilterOvpn != null) {
            btnFilterOvpn.setOnClickListener(v -> selectProtocol("OPENVPN"));
        }
    }

    private void setupNavigation() {
        navItemDashboard.setOnClickListener(v -> switchTab(0));
        navItemNodes.setOnClickListener(v -> switchTab(1));
        navItemRouting.setOnClickListener(v -> switchTab(2));
        navItemIppure.setOnClickListener(v -> switchTab(3));
    }

    private void switchTab(int index) {
        pageDashboard.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageNodes.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageRouting.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageIppure.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        updateTabNavStyle(index);

        if (index == 2 && !appsLoaded) {
            loadInstalledApplications();
        }

        if (index == 3 && !ippureWebLoaded) {
            loadIppurePage();
        }
    }

    private void updateTabNavStyle(int activeIndex) {
        int brandColor = ContextCompat.getColor(this, R.color.brand_primary);
        int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);

        // Reset all
        navPillDashboard.setBackground(null);
        navPillNodes.setBackground(null);
        navPillRouting.setBackground(null);
        navPillIppure.setBackground(null);

        navLabelDashboard.setTextColor(secondaryColor);
        navLabelNodes.setTextColor(secondaryColor);
        navLabelRouting.setTextColor(secondaryColor);
        navLabelIppure.setTextColor(secondaryColor);

        // Highlight active
        switch (activeIndex) {
            case 0:
                navPillDashboard.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelDashboard.setTextColor(brandColor);
                break;
            case 1:
                navPillNodes.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelNodes.setTextColor(brandColor);
                break;
            case 2:
                navPillRouting.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelRouting.setTextColor(brandColor);
                break;
            case 3:
                navPillIppure.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelIppure.setTextColor(brandColor);
                break;
        }
    }

    private void setupThemeToggle() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightMode == Configuration.UI_MODE_NIGHT_YES);

        btnThemeToggle.setText(isNight ? "🌙" : "☀️");
        btnThemeToggle.setContentDescription(isNight ? "切换为浅色模式" : "切换为深色模式");
        btnThemeToggle.setOnClickListener(v -> {
            if (isNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });
    }


    private void showLogConsoleBottomSheet() {
        if (logConsoleDialog != null && logConsoleDialog.isShowing()) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_log_console, null);
        dialog.setContentView(view);

        TextView tvConsoleLogs = view.findViewById(R.id.tv_console_logs);
        androidx.core.widget.NestedScrollView scrollConsoleLogs = view.findViewById(R.id.scroll_console_logs);
        TextView tvCountBadge = view.findViewById(R.id.tv_console_count_badge);
        TextView tabAll = view.findViewById(R.id.tab_log_all);
        TextView tabErrors = view.findViewById(R.id.tab_log_errors);
        TextView tabCore = view.findViewById(R.id.tab_log_core);
        TextView btnAutoScroll = view.findViewById(R.id.btn_toggle_autoscroll);

        tvBottomSheetLogs = tvConsoleLogs;
        scrollBottomSheetLogs = scrollConsoleLogs;

        final int[] filterMode = new int[]{0}; // 0: All, 1: Errors, 2: Core
        final boolean[] autoScroll = new boolean[]{true};

        Runnable updateTabsUi = () -> {
            if (tabAll != null) {
                tabAll.setTextColor(ContextCompat.getColor(this, filterMode[0] == 0 ? R.color.status_green : R.color.text_secondary));
            }
            if (tabErrors != null) {
                tabErrors.setTextColor(ContextCompat.getColor(this, filterMode[0] == 1 ? R.color.brand_primary : R.color.text_secondary));
            }
            if (tabCore != null) {
                tabCore.setTextColor(ContextCompat.getColor(this, filterMode[0] == 2 ? R.color.brand_primary : R.color.text_secondary));
            }
        };

        Runnable refreshLogsUi = () -> {
            if (tvConsoleLogs == null) return;
            List<LogManager.LogEntry> allEntries = LogManager.getEntries();
            List<LogManager.LogEntry> filtered = new ArrayList<>();
            for (LogManager.LogEntry e : allEntries) {
                if (filterMode[0] == 1) {
                    if (e.getLevel() == LogManager.Level.WARN || e.getLevel() == LogManager.Level.ERROR) {
                        filtered.add(e);
                    }
                } else if (filterMode[0] == 2) {
                    String tag = e.getTag().toUpperCase(Locale.ROOT);
                    if (tag.contains("WIREGUARD") || tag.contains("OPENVPN") || tag.contains("OVPN") || tag.contains("CORE") || tag.contains("TUN")) {
                        filtered.add(e);
                    }
                } else {
                    filtered.add(e);
                }
            }

            if (tvCountBadge != null) {
                tvCountBadge.setText(filtered.size() + " 条");
            }

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            for (LogManager.LogEntry e : filtered) {
                int start = ssb.length();
                ssb.append(e.getFormattedLine()).append("\n");
                int end = ssb.length();
                int color;
                switch (e.getLevel()) {
                    case ERROR:
                        color = ContextCompat.getColor(this, R.color.status_red);
                        break;
                    case WARN:
                        color = ContextCompat.getColor(this, R.color.status_orange);
                        break;
                    case DEBUG:
                        color = ContextCompat.getColor(this, R.color.text_muted);
                        break;
                    case INFO:
                    default:
                        color = ContextCompat.getColor(this, R.color.status_green);
                        break;
                }
                ssb.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvConsoleLogs.setText(ssb);
            if (autoScroll[0] && scrollConsoleLogs != null) {
                scrollConsoleLogs.post(() -> scrollConsoleLogs.fullScroll(View.FOCUS_DOWN));
            }
        };

        if (tabAll != null) {
            tabAll.setOnClickListener(v -> {
                filterMode[0] = 0;
                updateTabsUi.run();
                refreshLogsUi.run();
            });
        }
        if (tabErrors != null) {
            tabErrors.setOnClickListener(v -> {
                filterMode[0] = 1;
                updateTabsUi.run();
                refreshLogsUi.run();
            });
        }
        if (tabCore != null) {
            tabCore.setOnClickListener(v -> {
                filterMode[0] = 2;
                updateTabsUi.run();
                refreshLogsUi.run();
            });
        }
        if (btnAutoScroll != null) {
            btnAutoScroll.setOnClickListener(v -> {
                autoScroll[0] = !autoScroll[0];
                btnAutoScroll.setText(autoScroll[0] ? "自动滚屏: 开" : "自动滚屏: 关");
                btnAutoScroll.setTextColor(ContextCompat.getColor(this, autoScroll[0] ? R.color.status_green : R.color.text_secondary));
            });
        }

        updateTabsUi.run();
        refreshLogsUi.run();

        // Register live listener to append new incoming logs in real-time
        logConsoleListener = entry -> runOnUiThread(refreshLogsUi);
        LogManager.setListener(logConsoleListener);

        view.findViewById(R.id.btn_copy_logs).setOnClickListener(v -> {
            if (tvConsoleLogs != null) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("WireOpen Proxy Logs", tvConsoleLogs.getText());
                if (cm != null) {
                    cm.setPrimaryClip(clip);
                    Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            }
        });

        view.findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            LogManager.clear();
            refreshLogsUi.run();
            Toast.makeText(this, "控制台日志已清空", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_close_logs).setOnClickListener(v -> dialog.dismiss());

        logConsoleDialog = dialog;
        dialog.setOnDismissListener(d -> {
            LogManager.setListener(null);
            logConsoleListener = null;
            logConsoleDialog = null;
            tvBottomSheetLogs = null;
            scrollBottomSheetLogs = null;
        });

        dialog.show();
    }

    // ================= Settings & Connection Monitor BottomSheet =================
    private void showSettingsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        dialog.setContentView(view);

        TrafficStatsManager trafficMgr = TrafficStatsManager.getInstance();
        ProxyProfile active = repository.getActiveProfile();

        TextView tvOutboundIp = view.findViewById(R.id.settings_outbound_ip);
        TextView tvRemoteEndpoint = view.findViewById(R.id.settings_remote_endpoint);
        TextView tvProtocolChannel = view.findViewById(R.id.settings_protocol_channel);
        TextView tvTunIp = view.findViewById(R.id.settings_tun_ip);
        TextView tvRealtimeSpeed = view.findViewById(R.id.settings_realtime_speed);
        TextView tvUptime = view.findViewById(R.id.settings_uptime);
        TextView tvStatusBadge = view.findViewById(R.id.settings_connection_status_badge);

        boolean isConnected = (UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED);
        if (tvStatusBadge != null) {
            tvStatusBadge.setText(isConnected ? "ESTABLISHED" : "DISCONNECTED");
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, isConnected ? R.color.status_green : R.color.text_secondary));
        }

        if (active != null) {
            if (tvOutboundIp != null) tvOutboundIp.setText(trafficMgr.getOutboundIp());
            if (tvRemoteEndpoint != null) tvRemoteEndpoint.setText(active.getEndpointDisplay());
            if (tvProtocolChannel != null) tvProtocolChannel.setText(getProtocolChannelDisplay(active));
            if (tvTunIp != null) tvTunIp.setText(getVirtualTunDisplay(active));
        } else {
            if (tvOutboundIp != null) tvOutboundIp.setText("--");
            if (tvRemoteEndpoint != null) tvRemoteEndpoint.setText("未选择节点");
            if (tvProtocolChannel != null) tvProtocolChannel.setText("--");
            if (tvTunIp != null) tvTunIp.setText("--");
        }

        if (tvRealtimeSpeed != null) {
            tvRealtimeSpeed.setText("↓ " + formatSpeedNumber(currentDownSpeed) + " " + formatSpeedUnit(currentDownSpeed) +
                    " · ↑ " + formatSpeedNumber(currentUpSpeed) + " " + formatSpeedUnit(currentUpSpeed));
        }
        if (tvUptime != null) {
            tvUptime.setText(isConnected ? formatUptimeString() : "00:00:00");
        }

        updateSettingsStatsDisplay(view);

        refreshOutboundIpAsync(view);

        View manageCredentials = view.findViewById(R.id.btn_manage_ovpn_credentials);
        if (manageCredentials != null) {
            manageCredentials.setOnClickListener(v -> showCredentialsDialog());
        }

        view.findViewById(R.id.btn_reset_traffic_stats).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("重置历史流量统计")
                    .setMessage("确定要清空自安装以来的全部历史总流量和连接次数统计吗？（本次连接流量仍将保留）")
                    .setPositiveButton("重置", (d, w) -> {
                        trafficMgr.resetAllTimeStats();
                        updateSettingsStatsDisplay(view);
                        Toast.makeText(this, "历史所有流量统计已彻底重置为零", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        view.findViewById(R.id.btn_close_settings_top).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_close_settings_bottom).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String getProtocolChannelDisplay(ProxyProfile profile) {
        if (profile == null) return "--";
        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            int port = 0;
            if (profile.getWireGuardConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setWireGuardConfig(WireGuardConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getWireGuardConfig() != null) {
                port = profile.getWireGuardConfig().getEndpointPort();
            }
            return "WireGuard (UDP) · " + (port > 0 ? port : 51820);
        }
        if (profile.getOpenVpnConfig() == null && profile.getRawConfig() != null) {
            try {
                profile.setOpenVpnConfig(OpenVpnConfigParser.parse(profile.getRawConfig()));
            } catch (Exception ignored) {}
        }
        String proto = profile.getOpenVpnConfig() != null ? profile.getOpenVpnConfig().getProtocol() : "";
        if (proto == null || proto.trim().isEmpty()) proto = "udp";
        int port = profile.getOpenVpnConfig() != null ? profile.getOpenVpnConfig().getRemotePort() : 1194;
        return "OpenVPN (" + proto.trim().toUpperCase(Locale.ROOT) + ") · " + (port > 0 ? port : 1194);
    }

    private String getVirtualTunDisplay(ProxyProfile profile) {
        if (profile == null) return "--";
        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            if (profile.getWireGuardConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setWireGuardConfig(WireGuardConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getWireGuardConfig() != null && !profile.getWireGuardConfig().getAddresses().isEmpty()) {
                String address = profile.getWireGuardConfig().getAddresses().get(0);
                return address != null && !address.trim().isEmpty() ? address.trim() : "配置地址未知";
            }
            return "配置地址未知";
        }
        return "由 OpenVPN 服务端分配";
    }

    private void refreshOutboundIpAsync(View settingsView) {
        TextView tvOutboundIp = settingsView.findViewById(R.id.settings_outbound_ip);
        if (tvOutboundIp == null) return;
        tvOutboundIp.setText("查询中...");
        IpPureChecker.checkIpPureness(new IpPureChecker.Callback() {
            @Override
            public void onSuccess(IpPureResult result) {
                String ip = result != null ? result.getIp() : "";
                TrafficStatsManager.getInstance().setOutboundIp(ip);
                tvOutboundIp.setText(ip != null && !ip.isEmpty() && !"-".equals(ip) ? ip : "--");
            }

            @Override
            public void onError(String errorMessage) {
                tvOutboundIp.setText("获取失败");
                LogManager.log("MainActivity", "出口 IP 获取失败: " + errorMessage);
            }
        });
    }

    private void updateSettingsStatsDisplay(View view) {
        TrafficStatsManager trafficMgr = TrafficStatsManager.getInstance();
        TextView tvAllTimeDl = view.findViewById(R.id.settings_all_time_dl);
        TextView tvAllTimeUl = view.findViewById(R.id.settings_all_time_ul);
        TextView tvSessionTraffic = view.findViewById(R.id.settings_session_traffic);
        TextView tvAllTimeSum = view.findViewById(R.id.settings_all_time_sum);
        TextView tvConnectCount = view.findViewById(R.id.settings_connect_count);

        if (tvAllTimeDl != null) tvAllTimeDl.setText(TrafficStatsManager.formatBytes(trafficMgr.getAllTimeRxBytes()));
        if (tvAllTimeUl != null) tvAllTimeUl.setText(TrafficStatsManager.formatBytes(trafficMgr.getAllTimeTxBytes()));
        if (tvSessionTraffic != null) tvSessionTraffic.setText("↓ " + TrafficStatsManager.formatBytes(lastRxBytes) + " / ↑ " + TrafficStatsManager.formatBytes(lastTxBytes));
        if (tvAllTimeSum != null) tvAllTimeSum.setText(TrafficStatsManager.formatBytes(trafficMgr.getAllTimeTotalBytes()));
        if (tvConnectCount != null) tvConnectCount.setText(trafficMgr.getConnectionCount() + " 次");
    }

    // ================= OpenVPN Credentials Dialog =================
    private void showOpenVpnCredentialsForConnection(ProxyProfile profile) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_credentials, null);
        EditText etUser = view.findViewById(R.id.et_ovpn_username);
        EditText etPass = view.findViewById(R.id.et_ovpn_password);
        CheckBox cbRemember = view.findViewById(R.id.cb_remember_ovpn_credentials);
        if (etUser != null) etUser.setText("");
        if (etPass != null) etPass.setText("");
        if (cbRemember != null) cbRemember.setChecked(true);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        view.findViewById(R.id.btn_cancel_credentials).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_save_credentials).setOnClickListener(v -> {
            String username = etUser != null ? etUser.getText().toString().trim() : "";
            String password = etPass != null ? etPass.getText().toString() : "";
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入 OpenVPN 账号和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            CredentialManager credMgr = CredentialManager.getInstance();
            if (cbRemember != null && cbRemember.isChecked()) {
                credMgr.saveCredentials(username, password);
            } else {
                credMgr.setTemporaryCredentials(username, password);
            }
            dialog.dismiss();
            startVpnService();
        });
        dialog.show();
    }

    private void showCredentialsDialog() {
        CredentialManager credMgr = CredentialManager.getInstance();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_credentials, null);
        EditText etUser = view.findViewById(R.id.et_ovpn_username);
        EditText etPass = view.findViewById(R.id.et_ovpn_password);
        CheckBox cbRemember = view.findViewById(R.id.cb_remember_ovpn_credentials);
        View saveButton = view.findViewById(R.id.btn_save_credentials);

        if (etUser != null) etUser.setText(credMgr.getUsername());
        if (etPass != null) etPass.setText(credMgr.getPassword());
        if (cbRemember != null) cbRemember.setVisibility(View.GONE);
        if (saveButton instanceof MaterialButton) {
            ((MaterialButton) saveButton).setText("保存");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btn_cancel_credentials).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btn_save_credentials).setOnClickListener(v -> {
            String u = etUser != null ? etUser.getText().toString().trim() : "";
            String p = etPass != null ? etPass.getText().toString() : "";
            boolean clearing = u.isEmpty() && p.isEmpty();
            if (clearing) {
                credMgr.clearCredentials();
            } else {
                credMgr.saveCredentials(u, p);
            }
            dialog.dismiss();
            Toast.makeText(this, clearing
                    ? "OpenVPN 凭据已清除"
                    : "OpenVPN 专属凭据已保存", Toast.LENGTH_SHORT).show();
            LogManager.log("MainActivity", clearing
                    ? "已清除 OpenVPN 专属凭据"
                    : "已更新 OpenVPN 专属凭据 (用户: " + u + ")");
        });

        dialog.show();
    }

    private String formatUptimeString() {
        TrafficStatsManager stats = TrafficStatsManager.getInstance();
        if (stats != null && stats.isSessionActive()) {
            return stats.getSessionDurationFormatted();
        }
        if (!isTimerRunning || connectionStartTime <= 0) return "00:00:00";
        long elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
        long h = elapsed / 3600;
        long m = (elapsed % 3600) / 60;
        long s = elapsed % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private void normalizeImportedProfileNames() {
        for (ProxyProfile profile : repository.getProfiles()) {
            String name = profile.getName();
            String normalized = RegionDetector.normalizeDisplayName(name);
            if (!normalized.isEmpty() && !normalized.equals(name)) {
                profile.setName(normalized);
                repository.updateProfile(profile);
            }
        }
    }

    private void removeBuiltInDemoProfiles() {
        String[] demoIds = {
                "node-jp-1", "node-sg-1", "node-au-1", "node-us-1", "node-de-1", "node-hk-1"
        };
        for (String id : demoIds) {
            if (repository.getProfileById(id) != null) {
                repository.deleteProfile(id);
                LogManager.log("MainActivity", "已移除旧版内置演示节点: " + id);
            }
        }
    }

    private void selectProtocol(String proto) {
        this.activeProtocol = ("OPENVPN".equalsIgnoreCase(proto)) ? "OPENVPN" : "WIREGUARD";
        boolean isWg = "WIREGUARD".equalsIgnoreCase(this.activeProtocol);

        if (btnFilterWg != null && btnFilterOvpn != null) {
            if (isWg) {
                btnFilterWg.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.brand_primary));
                btnFilterWg.setTextColor(Color.WHITE);
                btnFilterOvpn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.surface_inner));
                btnFilterOvpn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else {
                btnFilterOvpn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.brand_primary));
                btnFilterOvpn.setTextColor(Color.WHITE);
                btnFilterWg.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.surface_inner));
                btnFilterWg.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
        renderNodeCards();
    }

    private void setupPresetRegions() {
        List<PresetPortItem> allPorts = PresetRegionManager.getInstance().getAllPorts();
        int totalNodes = allPorts.size();

        int wgCount = 0;
        int ovpnCount = 0;
        for (PresetPortItem item : allPorts) {
            if (item.getProtocolType() == ProtocolType.WIREGUARD) {
                wgCount++;
            } else {
                ovpnCount++;
            }
        }

        if (btnFilterWg != null) {
            btnFilterWg.setText("⚡ WireGuard (" + wgCount + ")");
        }
        if (btnFilterOvpn != null) {
            btnFilterOvpn.setText("🛡️ OpenVPN (" + ovpnCount + ")");
        }

        selectProtocol(activeProtocol);
    }

    private List<PresetPortItem> getVisibleNodePorts() {
        List<PresetPortItem> filtered = new ArrayList<>();
        for (PresetPortItem item : PresetRegionManager.getInstance().getAllPorts()) {
            if (item == null) continue;

            if (searchQuery != null && !searchQuery.isEmpty()) {
                if (!matchesNodeSearch(item, null, null)) {
                    continue;
                }
            }

            if ("OPENVPN".equalsIgnoreCase(activeProtocol)) {
                if (item.getProtocolType() != ProtocolType.OPENVPN) continue;
            } else if (item.getProtocolType() != ProtocolType.WIREGUARD) {
                continue;
            }

            filtered.add(item);
        }
        return filtered;
    }

    private void showPortSelectionDialog() {
        final int[] ports = new int[]{443, 80, 53, 123, 1194, 65142};
        String[] portNames = new String[ports.length];
        int selectedIndex = 0;
        for (int i = 0; i < ports.length; i++) {
            portNames[i] = String.valueOf(ports[i]);
            if (ports[i] == currentSelectedPort) {
                selectedIndex = i;
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择常用端口")
                .setSingleChoiceItems(portNames, selectedIndex, (dialog, which) -> {
                    int newPort = ports[which];
                    dialog.dismiss();
                    applySelectedPort(newPort);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applySelectedPort(int newPort) {
        currentSelectedPort = newPort;
        isPortManuallyOverridden = true;
        if (btnNodePortSelect != null) {
            btnNodePortSelect.setText(currentSelectedPort + " ▾");
        }

        ProxyProfile active = repository.getActiveProfile();
        if (active != null) {
            if (UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED
                    || lastConnectionState == ConnectionState.CONNECTED) {
                startVpnService();
            }
        }

        Toast.makeText(this, "已应用端口: " + currentSelectedPort, Toast.LENGTH_SHORT).show();
        renderNodeCards();
        updateActiveProfileUi();
    }

    private void applyPortToProfile(ProxyProfile profile, int port) {
        if (profile == null) return;
        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            if (profile.getRawConfig() != null) {
                // Support IPv6 bracketed addresses like [2001:db8::1]:51820 without truncating colons
                profile.setRawConfig(profile.getRawConfig().replaceAll(
                        "(?i)(Endpoint\\s*=\\s*(\\[[^\\]\r\n]+\\]|[^:\\s\r\n]+))\\s*:\\s*\\d+",
                        "$1:" + port));
            }
            if (profile.getWireGuardConfig() != null) {
                profile.getWireGuardConfig().setEndpointPort(port);
            } else if (profile.getRawConfig() != null) {
                try {
                    profile.setWireGuardConfig(WireGuardConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
        } else if (profile.getProtocolType() == ProtocolType.OPENVPN) {
            if (profile.getRawConfig() != null) {
                String updated = profile.getRawConfig().replaceAll(
                        "(?i)(remote\\s+\\S+\\s+)\\d+",
                        "$1" + port);
                updated = updated.replaceAll(
                        "(?m)(?i)(^\\s*port\\s+)\\d+",
                        "$1" + port);
                profile.setRawConfig(updated);
            }
            if (profile.getOpenVpnConfig() != null) {
                profile.getOpenVpnConfig().setRemotePort(port);
            } else if (profile.getRawConfig() != null) {
                try {
                    profile.setOpenVpnConfig(OpenVpnConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
        }
    }

    private void toggleAllGroups() {
        if (collapsedCountries.isEmpty()) {
            for (PresetPortItem item : getVisibleNodePorts()) {
                collapsedCountries.add(getCountryName(item));
            }
            allGroupsExpanded = false;
            if (btnToggleAllGroups != null) btnToggleAllGroups.setText("全部展开");
        } else {
            collapsedCountries.clear();
            allGroupsExpanded = true;
            if (btnToggleAllGroups != null) btnToggleAllGroups.setText("全部折叠");
        }
        renderNodeCards();
    }

    private void toggleSortMode() {
        sortByLatency = !sortByLatency;
        updateSortSpeedometerUi();
        renderNodeCards();
    }

    private void updateSortSpeedometerUi() {
        if (btnSortSpeedometer == null) return;
        if (sortByLatency) {
            btnSortSpeedometer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.brand_primary));
            btnSortSpeedometer.setIconTint(ColorStateList.valueOf(Color.WHITE));
        } else {
            btnSortSpeedometer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.surface_inner));
            btnSortSpeedometer.setIconTint(ContextCompat.getColorStateList(this, R.color.text_secondary));
        }
    }

    private static class CountryGroup {
        final String countryCode;
        final String countryName;
        int flagRes;
        final List<PresetPortItem> items = new ArrayList<>();

        CountryGroup(String countryCode, String countryName, int flagRes) {
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.flagRes = flagRes;
        }

        long getMinLatency() {
            long min = Long.MAX_VALUE;
            for (PresetPortItem p : items) {
                Long lat = p.getLatencyMs();
                if (lat != null && lat > 0) {
                    if (lat < min) min = lat;
                }
            }
            return min;
        }
    }

    private String getCountryName(PresetPortItem item) {
        if (item == null) return "全球代理节点";
        String effective = item.getEffectiveChineseName();
        if (effective != null && !effective.isEmpty()) {
            if (effective.startsWith("中国 · 香港")) return "中国香港";
            if (effective.startsWith("中国 · 台湾")) return "中国台湾";
            if (effective.contains(" · ")) {
                return effective.split(" · ", 2)[0].trim();
            }
        }

        String code = item.getCountryCode();
        String cByCode = RegionDetector.getCountryNameByCode(code);
        if (cByCode != null) {
            return cByCode;
        }

        RegionDetector.RegionInfo regInfo = RegionDetector.detect(
                item.getHost(),
                item.getRegionName(),
                item.getDedicatedName(),
                effective
        );
        if (regInfo != null) {
            String cName = regInfo.getName();
            if (cName != null && !cName.isEmpty() && !"全球代理节点".equals(cName)) {
                if (cName.startsWith("中国 · 香港")) return "中国香港";
                if (cName.startsWith("中国 · 台湾")) return "中国台湾";
                if (cName.contains(" · ")) return cName.split(" · ", 2)[0].trim();
                return cName.trim();
            }
        }

        return (effective != null && !effective.trim().isEmpty()) ? effective.trim() : "全球代理节点";
    }

    private static final String[] KNOWN_CITIES = {
        // Multi-word cities
        "Fake-St-Petersburg", "Fake St Petersburg", "Fake-Mumbai", "Fake Mumbai",
        "Salt-Lake-City", "Salt Lake City", "San-Francisco", "San Francisco",
        "San-Jose", "San Jose", "Santa-Clara", "Santa Clara", "San-Salvador", "San Salvador",
        "Sao-Paulo", "Sao Paulo", "South-Bend", "South Bend",
        "Los-Angeles", "Los Angeles", "New-York", "New York", "New-Jersey", "New Jersey",
        "New-Delhi", "New Delhi", "Washington-DC", "Washington DC", "Washington",
        "Buenos-Aires", "Buenos Aires", "Guatemala-City", "Guatemala City",
        "Panama-City", "Panama City", "Kansas-City", "Kansas City",
        "Kuala-Lumpur", "Kuala Lumpur", "Novi-Travnik", "Novi Travnik",
        "Tel-Aviv", "Tel Aviv", "Hong-Kong", "Hong Kong", "Troll-Station", "Troll Station",
        // Single-word cities
        "Adelaide", "Amsterdam", "Ashburn", "Ashdod", "Asuncion", "Athens", "Atlanta",
        "Auckland", "Bali", "Bangkok", "Barcelona", "Belgrade", "Bend", "Berlin",
        "Bogota", "Bordeaux", "Boston", "Bratislava", "Brisbane", "Brussels",
        "Bucharest", "Budapest", "Buffalo", "Calgary", "Charlotte", "Chicago",
        "Chisinau", "Cleveland", "Copenhagen", "Dallas", "Denver", "Detroit",
        "Dubai", "Dublin", "Edinburgh", "Frankfurt", "Gdansk", "Halifax",
        "Hanoi", "Helsinki", "Honolulu", "Houston", "Istanbul", "Jakarta",
        "Johannesburg", "Kyiv", "Lagos", "Las-Vegas", "Las Vegas", "Lima", "Limassol",
        "Lisbon", "Ljubljana", "London", "Luxembourg", "Madrid", "Manchester",
        "Manila", "Marseille", "Melbourne", "Miami", "Milan", "Montevideo",
        "Montreal", "Moscow", "Mumbai", "Nairobi", "Nicosia", "Oslo",
        "Palermo", "Paris", "Perth", "Philadelphia", "Phoenix", "Prague",
        "Queretaro", "Quito", "Raleigh", "Reykjavik", "Riga", "Santiago",
        "Sarajevo", "Seattle", "Seoul", "Shamchester", "Singapore", "Skopje",
        "Sofia", "Stockholm", "Surabaya", "Sydney", "Taipei", "Tallinn",
        "Tampa", "Tbilisi", "Tirana", "Tokyo", "Toronto", "Vancouver",
        "Vienna", "Vilnius", "Warsaw", "Winnipeg", "Zagreb", "Zurich"
    };

    public String getCityOrNodeName(PresetPortItem item) {
        return getCityOrNodeName(item, this.repository);
    }

    public static String getCityOrNodeName(PresetPortItem item, ProfileRepository repo) {
        if (item == null) return "节点";
        String city = extractChineseCity(item);
        String subRegion = extractSubRegion(item, city, repo);
        if (subRegion != null && !subRegion.isEmpty()) {
            return city + " · " + subRegion;
        }
        return city;
    }

    public static String extractChineseCity(PresetPortItem item) {
        String name = item.getEffectiveChineseName();
        if (name == null || name.trim().isEmpty()) {
            name = item.getRegionName();
        }
        if (name == null || name.trim().isEmpty()) {
            return "节点";
        }
        name = name.trim();
        if (name.startsWith("中国 · 香港")) {
            return "香港";
        }
        if (name.startsWith("中国 · 台湾")) {
            String[] parts = name.split(" · ");
            return parts.length > 2 ? parts[2].trim() : (parts.length > 1 ? parts[1].trim() : "台湾");
        }
        if (name.contains(" · ")) {
            String[] parts = name.split(" · ", 2);
            return parts[1].trim();
        }
        return name;
    }

    public String extractSubRegion(PresetPortItem item, String chineseCity) {
        return extractSubRegion(item, chineseCity, this.repository);
    }

    public static String extractSubRegion(PresetPortItem item, String chineseCity, ProfileRepository repo) {
        if (item == null) return null;
        String raw = null;
        if (item.getRawProfileName() != null && !item.getRawProfileName().trim().isEmpty()) {
            raw = item.getRawProfileName().trim();
        }
        if ((raw == null || raw.isEmpty()) && item.getProfileId() != null && repo != null) {
            ProxyProfile p = repo.getProfileById(item.getProfileId());
            if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                raw = p.getName().trim();
            }
        }
        if ((raw == null || raw.isEmpty()) && item.getDedicatedName() != null && !item.getDedicatedName().trim().isEmpty()) {
            raw = item.getDedicatedName().trim();
        }
        if ((raw == null || raw.isEmpty()) && item.getProfileId() != null && !item.getProfileId().trim().isEmpty()) {
            raw = item.getProfileId().trim();
        }
        if ((raw == null || raw.isEmpty()) && item.getRegionName() != null && !item.getRegionName().trim().isEmpty()) {
            raw = item.getRegionName().trim();
        }
        return extractSubRegionFromRaw(raw, chineseCity);
    }

    public static String extractSubRegionFromRaw(String raw, String chineseCity) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Strip file extensions
        s = s.replaceAll("(?i)\\.(conf|ovpn|txt|json)$", "").trim();

        // Strip protocol tag & optional port at end, e.g. -WG, _WG, -OVPN, -UDP, -TCP, -443
        s = s.replaceAll("(?i)[-_](WG|OVPN|UDP|TCP|wireguard|openvpn)(?:[-_]\\d+)?$", "").trim();
        
        // Strip only well-known port numbers at the end (prevent stripping numeric aliases like -1984, -69, -67)
        s = s.replaceAll("(?i)[-_](?:port[-_]?)?(?:443|80|53|1194|51820|8080|8443|65142)$", "").trim();

        // Strip provider prefixes
        s = s.replaceAll("(?i)^(?:Windscribe|Proton|Mullvad|NordVPN|Nord|Surfshark|ExpressVPN|Express|PIA|PureVPN)[-_\\s]+", "").trim();

        // If string already contains " · ", take the last part
        if (s.contains(" · ")) {
            String[] parts = s.split(" · ");
            if (parts.length > 1) {
                return parts[parts.length - 1].trim();
            }
        }

        // Try matching known city prefixes
        for (String city : KNOWN_CITIES) {
            String cityLower = city.toLowerCase(Locale.ROOT);
            String sLower = s.toLowerCase(Locale.ROOT);
            if (sLower.startsWith(cityLower)) {
                if (sLower.length() > cityLower.length()) {
                    char nextChar = s.charAt(city.length());
                    if (nextChar != '-' && nextChar != '_' && nextChar != ' ') {
                        continue;
                    }
                }
                String remainder = s.substring(city.length()).trim();
                // Strip leading delimiters
                remainder = remainder.replaceAll("^[-_\\s]+", "").trim();
                if (!remainder.isEmpty()) {
                    String sub = remainder.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").trim();
                    if (!sub.isEmpty() && !sub.equalsIgnoreCase(city)) {
                        return sub;
                    }
                }
                return null;
            }
        }

        // Generic fallback: if hyphen or underscore separated, e.g. City-Subregion
        if (s.contains("-") || s.contains("_")) {
            String[] parts = s.split("[-_]+");
            if (parts.length >= 2) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(parts[i]);
                }
                String sub = sb.toString().trim();
                if (!sub.isEmpty()) {
                    return sub;
                }
            }
        }

        return null;
    }

    private boolean matchesNodeSearch(PresetPortItem item, String countryName, String countryCode) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        if (item == null) return false;

        String displayName = getCityOrNodeName(item).toLowerCase(Locale.ROOT);
        if (displayName.contains(searchQuery)) return true;

        String rawName = (item.getRawProfileName() != null ? item.getRawProfileName() : "").toLowerCase(Locale.ROOT);
        if (rawName.contains(searchQuery)) return true;

        String sub = extractSubRegion(item, "");
        if (sub != null && sub.toLowerCase(Locale.ROOT).contains(searchQuery)) return true;

        String dedicated = (item.getDedicatedName() != null ? item.getDedicatedName() : "").toLowerCase(Locale.ROOT);
        if (dedicated.contains(searchQuery)) return true;

        String profileId = (item.getProfileId() != null ? item.getProfileId() : "").toLowerCase(Locale.ROOT);
        if (profileId.contains(searchQuery)) return true;

        String host = item.getHost() != null ? item.getHost().toLowerCase(Locale.ROOT) : "";
        if (host.contains(searchQuery)) return true;

        String effName = item.getEffectiveChineseName() != null ? item.getEffectiveChineseName().toLowerCase(Locale.ROOT) : "";
        if (effName.contains(searchQuery)) return true;

        if (countryName != null && countryName.toLowerCase(Locale.ROOT).contains(searchQuery)) return true;
        if (countryCode != null && countryCode.toLowerCase(Locale.ROOT).contains(searchQuery)) return true;

        String cName = getCountryName(item).toLowerCase(Locale.ROOT);
        if (cName.contains(searchQuery)) return true;

        String cCode = item.getCountryCode() != null ? item.getCountryCode().toLowerCase(Locale.ROOT) : "";
        if (cCode.contains(searchQuery)) return true;

        String port = String.valueOf(item.getPort());
        if (port.contains(searchQuery)) return true;

        return false;
    }

    private void registerPortViews(String uniqueId, TextView badge, View card) {
        if (!portBadgeViews.containsKey(uniqueId)) {
            portBadgeViews.put(uniqueId, new ArrayList<>());
        }
        portBadgeViews.get(uniqueId).add(badge);

        if (!portCardViews.containsKey(uniqueId)) {
            portCardViews.put(uniqueId, new ArrayList<>());
        }
        portCardViews.get(uniqueId).add(card);
    }

    private void setPortViewsTesting(String uniqueId) {
        List<TextView> badges = portBadgeViews.get(uniqueId);
        if (badges != null) {
            for (TextView badge : badges) {
                badge.setText("...");
                badge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            }
        }
        List<View> cards = portCardViews.get(uniqueId);
        if (cards != null) {
            for (View card : cards) {
                View btnPing = card.findViewById(R.id.btn_node_ping);
                View dotLatency = card.findViewById(R.id.dot_node_latency);
                if (btnPing != null) btnPing.setBackgroundResource(R.drawable.bg_latency_yellow);
                if (dotLatency != null) dotLatency.setBackgroundResource(R.drawable.bg_dot_yellow);
            }
        }
    }

    private void toggleFavorite(PresetPortItem item) {
        if (item == null) return;
        String id = item.getUniqueId();
        if (favoriteNodeIds.contains(id)) {
            favoriteNodeIds.remove(id);
            item.setFavorite(false);
            Toast.makeText(this, "已取消收藏: " + item.getEffectiveChineseName(), Toast.LENGTH_SHORT).show();
        } else {
            favoriteNodeIds.add(id);
            item.setFavorite(true);
            Toast.makeText(this, "⭐ 已添加至常用收藏: " + item.getEffectiveChineseName(), Toast.LENGTH_SHORT).show();
        }
        if (favoritesPrefs != null) {
            favoritesPrefs.edit().putStringSet(KEY_FAVORITE_SET, new HashSet<>(favoriteNodeIds)).apply();
        }
        renderNodeCards();
    }

    private void testCountrySpeed(CountryGroup group, View btnCountrySpeedTest) {
        if (group == null || group.items.isEmpty()) return;
        if (btnCountrySpeedTest != null) {
            btnCountrySpeedTest.setEnabled(false);
        }
        Toast.makeText(this, "正在测速 " + group.countryName + " 节点...", Toast.LENGTH_SHORT).show();
        for (PresetPortItem item : group.items) {
            setPortViewsTesting(item.getUniqueId());
        }
        PortSpeedTester.testAllPorts(group.items, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem item, long latencyMs) {
                scheduleLatencyUiUpdate(item);
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {
                runOnUiThread(() -> {
                    if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        return;
                    }
                    latencyUiHandler.removeCallbacks(latencyUiRunnable);
                    latencyUiRunnable.run();
                    if (btnCountrySpeedTest != null) {
                        btnCountrySpeedTest.setEnabled(true);
                    }
                    Toast.makeText(MainActivity.this, group.countryName + " 测速完毕 (" + successCount + "/" + totalCount + ")", Toast.LENGTH_SHORT).show();
                    if (sortByLatency) {
                        renderNodeCards();
                    }
                    updateActiveProfileUi();
                });
            }
        });
    }

    private View createNodeCardView(
            LayoutInflater inflater,
            ViewGroup parent,
            PresetPortItem item,
            ProxyProfile active,
            boolean isSearching,
            String countryName,
            String countryCode,
            String nameSuffix) {
        View card = inflater.inflate(R.layout.item_node_card, parent, false);

        ImageView ivFlag = card.findViewById(R.id.iv_node_flag);
        TextView tvName = card.findViewById(R.id.tv_node_chinese_name);
        TextView tvBadge = card.findViewById(R.id.tv_node_badge);
        TextView tvProtoPort = card.findViewById(R.id.tv_node_proto_port);
        TextView btnFavorite = card.findViewById(R.id.btn_node_favorite);
        View btnPing = card.findViewById(R.id.btn_node_ping);
        View dotLatency = card.findViewById(R.id.dot_node_latency);
        TextView tvLatency = card.findViewById(R.id.tv_node_latency);

        ivFlag.setImageResource(item.getFlagDrawableRes());
        String baseDisplayName = getCityOrNodeName(item);
        if (nameSuffix != null && !nameSuffix.isEmpty()) {
            tvName.setText(baseDisplayName + " " + nameSuffix);
        } else {
            tvName.setText(baseDisplayName);
        }
        tvProtoPort.setText(item.getShortProtoTag() + " · " + item.getPort());
        tvProtoPort.setTextColor(ContextCompat.getColor(this,
                item.getProtocolType() == ProtocolType.WIREGUARD
                        ? R.color.wireguard_badge_text : R.color.brand_primary));
        tvProtoPort.setBackgroundResource(item.getProtocolType() == ProtocolType.WIREGUARD
                ? R.drawable.bg_wireguard_badge : R.drawable.badge_rounded_bg);

        boolean isActive = (active != null)
                && (active.getId().equals(item.getProfileId()) || active.getId().equals(item.getUniqueId()));

        if (isActive) {
            card.setBackgroundResource(R.drawable.bg_node_card_active);
            if (tvBadge != null) {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText(UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED ? "已连接" : "当前");
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            }
        } else {
            boolean isMatching = isSearching && matchesNodeSearch(item, countryName, countryCode);
            if (isMatching) {
                card.setBackgroundResource(R.drawable.bg_node_card_active);
            } else {
                card.setBackgroundResource(R.drawable.bg_node_card_inactive);
            }
            if (tvBadge != null) {
                tvBadge.setVisibility(View.GONE);
            }
        }

        // ⭐ 收藏按钮状态和交互
        if (btnFavorite != null) {
            boolean isFav = item.isFavorite();
            btnFavorite.setText(isFav ? "★" : "☆");
            btnFavorite.setTextColor(isFav ? Color.parseColor("#F59E0B") : ContextCompat.getColor(this, R.color.text_muted));
            btnFavorite.setOnClickListener(v -> toggleFavorite(item));
        }

        updateNodeLatencyDisplay(item, btnPing, dotLatency, tvLatency);
        btnPing.setOnClickListener(v -> testSinglePortLatency(item, tvLatency, dotLatency, btnPing));

        View.OnClickListener connectListener = v -> {
            ProxyProfile targetProfile = repository.getProfileById(item.getProfileId());
            if (targetProfile == null) {
                targetProfile = item.toProxyProfile();
                repository.addProfile(targetProfile);
            }
            repository.setActiveProfileId(targetProfile.getId());
            refreshProfiles();

            // When switching node card, reset manual override and reflect node's native port
            isPortManuallyOverridden = false;
            currentSelectedPort = item.getPort();
            if (btnNodePortSelect != null) {
                btnNodePortSelect.setText(currentSelectedPort + " ▾");
            }

            boolean isConnected = (UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED)
                    || (lastConnectionState == ConnectionState.CONNECTED);
            if (isConnected) {
                startVpnService();
            }
            Toast.makeText(MainActivity.this, "已设为当前节点: " + item.getEffectiveChineseName(), Toast.LENGTH_SHORT).show();
            LogManager.log("MainActivity", "激活节点: " + item.getEffectiveChineseName() + " [" + item.getShortProtoTag() + " :" + item.getPort() + "]");
            renderNodeCards();
            updateActiveProfileUi();
        };

        card.setOnClickListener(connectListener);

        card.setOnLongClickListener(v -> {
            androidx.appcompat.app.AlertDialog.Builder builder =
                    new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("节点操作 · " + item.getEffectiveChineseName());
            builder.setItems(new String[]{"编辑配置", "删除节点"}, (dialog, which) -> {
                if (which == 0) {
                    showProfileConfigEditor(item);
                } else {
                    deleteNodeConfirm(item);
                }
            });
            builder.show();
            return true;
        });

        registerPortViews(item.getUniqueId(), tvLatency, card);
        return card;
    }

    private void renderNodeCards() {
        if (layoutNodesContainer == null) return;
        layoutNodesContainer.removeAllViews();
        portBadgeViews.clear();
        portCardViews.clear();

        List<PresetPortItem> filtered = getVisibleNodePorts();

        if (filtered.isEmpty()) {
            if (cardEmptyState != null) {
                cardEmptyState.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (cardEmptyState != null) {
            cardEmptyState.setVisibility(View.GONE);
        }

        ProxyProfile active = repository.getActiveProfile();

        // 整理收藏节点列表
        List<PresetPortItem> favoriteItems = new ArrayList<>();
        Map<String, CountryGroup> groupMap = new LinkedHashMap<>();
        for (PresetPortItem item : filtered) {
            boolean isFav = favoriteNodeIds.contains(item.getUniqueId()) || favoriteNodeIds.contains(item.getProfileId());
            item.setFavorite(isFav);
            if (isFav) {
                favoriteItems.add(item);
            }

            String cName = getCountryName(item);
            CountryGroup group = groupMap.get(cName);
            if (group == null) {
                group = new CountryGroup(item.getCountryCode(), cName, item.getFlagDrawableRes());
                groupMap.put(cName, group);
            }
            group.items.add(item);
        }

        List<CountryGroup> groupList = new ArrayList<>(groupMap.values());
        Collator collator = Collator.getInstance(Locale.CHINESE);

        if (sortByLatency) {
            Collections.sort(groupList, (a, b) -> {
                long latA = a.getMinLatency();
                long latB = b.getMinLatency();
                int cmp = Long.compare(latA, latB);
                return cmp != 0 ? cmp : collator.compare(a.countryName, b.countryName);
            });
            for (CountryGroup g : groupList) {
                Collections.sort(g.items, (a, b) -> {
                    long latA = a.getLatencyMs() != null && a.getLatencyMs() > 0 ? a.getLatencyMs() : Long.MAX_VALUE;
                    long latB = b.getLatencyMs() != null && b.getLatencyMs() > 0 ? b.getLatencyMs() : Long.MAX_VALUE;
                    int cmp = Long.compare(latA, latB);
                    return cmp != 0 ? cmp : collator.compare(getCityOrNodeName(a), getCityOrNodeName(b));
                });
            }
            Collections.sort(favoriteItems, (a, b) -> {
                long latA = a.getLatencyMs() != null && a.getLatencyMs() > 0 ? a.getLatencyMs() : Long.MAX_VALUE;
                long latB = b.getLatencyMs() != null && b.getLatencyMs() > 0 ? b.getLatencyMs() : Long.MAX_VALUE;
                int cmp = Long.compare(latA, latB);
                return cmp != 0 ? cmp : collator.compare(getCityOrNodeName(a), getCityOrNodeName(b));
            });
        } else {
            Collections.sort(groupList, (a, b) -> collator.compare(a.countryName, b.countryName));
            for (CountryGroup g : groupList) {
                Collections.sort(g.items, (a, b) -> collator.compare(getCityOrNodeName(a), getCityOrNodeName(b)));
            }
            Collections.sort(favoriteItems, (a, b) -> collator.compare(getCityOrNodeName(a), getCityOrNodeName(b)));
        }

        if (btnToggleAllGroups != null && (searchQuery == null || searchQuery.isEmpty())) {
            btnToggleAllGroups.setText(collapsedCountries.isEmpty() ? "全部折叠" : "全部展开");
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        boolean isSearching = searchQuery != null && !searchQuery.isEmpty();

        // 1. 若有收藏节点，置顶渲染【⭐ 常用收藏】抽屉
        if (!favoriteItems.isEmpty()) {
            View favView = inflater.inflate(R.layout.item_country_group, layoutNodesContainer, false);
            ImageView ivFavFlag = favView.findViewById(R.id.iv_country_flag);
            TextView tvFavName = favView.findViewById(R.id.tv_country_name);
            TextView tvFavNodeCount = favView.findViewById(R.id.tv_country_node_count);
            View btnFavSpeed = favView.findViewById(R.id.btn_country_speed_test);
            TextView tvFavArrow = favView.findViewById(R.id.tv_country_arrow);
            View layoutFavHeader = favView.findViewById(R.id.layout_country_header);
            LinearLayout layoutFavNodes = favView.findViewById(R.id.layout_country_nodes);
            View dividerFav = favView.findViewById(R.id.divider_country);

            ivFavFlag.setImageResource(R.drawable.flag_global);
            tvFavName.setText("⭐ 常用收藏");
            tvFavNodeCount.setText(String.valueOf(favoriteItems.size()));

            boolean isFavExpanded = isSearching || favoriteDrawerExpanded;
            layoutFavNodes.setVisibility(isFavExpanded ? View.VISIBLE : View.GONE);
            if (dividerFav != null) {
                dividerFav.setVisibility(isFavExpanded ? View.VISIBLE : View.GONE);
            }
            tvFavArrow.setText(isFavExpanded ? "▲" : "▼");

            layoutFavHeader.setOnClickListener(v -> {
                favoriteDrawerExpanded = layoutFavNodes.getVisibility() != View.VISIBLE;
                layoutFavNodes.setVisibility(favoriteDrawerExpanded ? View.VISIBLE : View.GONE);
                if (dividerFav != null) {
                    dividerFav.setVisibility(favoriteDrawerExpanded ? View.VISIBLE : View.GONE);
                }
                tvFavArrow.setText(favoriteDrawerExpanded ? "▲" : "▼");
            });

            if (btnFavSpeed != null) {
                CountryGroup favGroup = new CountryGroup("FAV", "常用收藏", R.drawable.flag_global);
                favGroup.items.addAll(favoriteItems);
                btnFavSpeed.setOnClickListener(v -> testCountrySpeed(favGroup, btnFavSpeed));
            }

            Map<String, Integer> favNameCounts = new HashMap<>();
            for (PresetPortItem item : favoriteItems) {
                String n = getCityOrNodeName(item);
                favNameCounts.put(n, favNameCounts.getOrDefault(n, 0) + 1);
            }
            Map<String, Integer> favNameIdxMap = new HashMap<>();

            for (PresetPortItem item : favoriteItems) {
                String baseDisplayName = getCityOrNodeName(item);
                String suffix = "";
                if (favNameCounts.getOrDefault(baseDisplayName, 0) > 1) {
                    int idx = favNameIdxMap.getOrDefault(baseDisplayName, 0) + 1;
                    favNameIdxMap.put(baseDisplayName, idx);
                    suffix = "#" + idx;
                }
                View card = createNodeCardView(inflater, layoutFavNodes, item, active, isSearching, "常用收藏", "FAV", suffix);
                layoutFavNodes.addView(card);
            }

            layoutNodesContainer.addView(favView);
        }

        // 2. 正常遍历各国抽屉
        for (CountryGroup group : groupList) {
            View countryView = inflater.inflate(R.layout.item_country_group, layoutNodesContainer, false);
            ImageView ivCountryFlag = countryView.findViewById(R.id.iv_country_flag);
            TextView tvCountryName = countryView.findViewById(R.id.tv_country_name);
            TextView tvCountryNodeCount = countryView.findViewById(R.id.tv_country_node_count);
            View btnCountrySpeed = countryView.findViewById(R.id.btn_country_speed_test);
            TextView tvCountryArrow = countryView.findViewById(R.id.tv_country_arrow);
            View layoutCountryHeader = countryView.findViewById(R.id.layout_country_header);
            LinearLayout layoutCountryNodes = countryView.findViewById(R.id.layout_country_nodes);
            View dividerCountry = countryView.findViewById(R.id.divider_country);

            ivCountryFlag.setImageResource(group.flagRes);
            tvCountryName.setText(group.countryName);
            tvCountryNodeCount.setText(String.valueOf(group.items.size()));

            boolean isExpanded = isSearching || !collapsedCountries.contains(group.countryName);
            layoutCountryNodes.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            if (dividerCountry != null) {
                dividerCountry.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            }
            tvCountryArrow.setText(isExpanded ? "▲" : "▼");

            layoutCountryHeader.setOnClickListener(v -> {
                boolean currentlyExpanded = layoutCountryNodes.getVisibility() == View.VISIBLE;
                boolean nextExpanded = !currentlyExpanded;
                layoutCountryNodes.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
                if (dividerCountry != null) {
                    dividerCountry.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
                }
                tvCountryArrow.setText(nextExpanded ? "▲" : "▼");
                if (nextExpanded) {
                    collapsedCountries.remove(group.countryName);
                } else {
                    collapsedCountries.add(group.countryName);
                }
                if (btnToggleAllGroups != null && (searchQuery == null || searchQuery.isEmpty())) {
                    btnToggleAllGroups.setText(collapsedCountries.isEmpty() ? "全部折叠" : "全部展开");
                }
            });

            if (btnCountrySpeed != null) {
                btnCountrySpeed.setOnClickListener(v -> testCountrySpeed(group, btnCountrySpeed));
            }

            Map<String, Integer> nameCounts = new HashMap<>();
            for (PresetPortItem item : group.items) {
                String n = getCityOrNodeName(item);
                nameCounts.put(n, nameCounts.getOrDefault(n, 0) + 1);
            }
            Map<String, Integer> nameIdxMap = new HashMap<>();

            for (PresetPortItem item : group.items) {
                String baseDisplayName = getCityOrNodeName(item);
                String suffix = "";
                if (nameCounts.getOrDefault(baseDisplayName, 0) > 1) {
                    int idx = nameIdxMap.getOrDefault(baseDisplayName, 0) + 1;
                    nameIdxMap.put(baseDisplayName, idx);
                    suffix = "#" + idx;
                }
                View card = createNodeCardView(inflater, layoutCountryNodes, item, active, isSearching, group.countryName, group.countryCode, suffix);
                layoutCountryNodes.addView(card);
            }

            layoutNodesContainer.addView(countryView);
        }
    }


    /**
     * Opens the saved raw configuration in an in-app editor.
     * Saving reparses the edited file immediately, so the node card always reflects
     * the Endpoint/remote port actually present in the configuration.
     */
    private void showProfileConfigEditor(PresetPortItem item) {
        if (item == null || item.getProfileId() == null) {
            Toast.makeText(this, "该节点没有可编辑的配置文件", Toast.LENGTH_SHORT).show();
            return;
        }

        ProxyProfile profile = repository.getProfileById(item.getProfileId());
        if (profile == null || profile.getRawConfig() == null) {
            Toast.makeText(this, "无法读取节点配置文件", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText editor = new EditText(this);
        editor.setText(profile.getRawConfig());
        editor.setSelection(editor.length());
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setSingleLine(false);
        editor.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setVerticalScrollBarEnabled(true);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        int editorHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        scrollView.setMinimumHeight(dp(260));
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, editorHeight
        ));
        scrollView.addView(editor, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        String extension = profile.getProtocolType() == ProtocolType.WIREGUARD ? ".conf" : ".ovpn";
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("编辑配置 · " + RegionDetector.normalizeDisplayName(profile.getName()) + extension)
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            android.widget.Button saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (saveButton == null) return;
            saveButton.setOnClickListener(v -> saveEditedProfile(profile, editor.getText().toString(), dialog));
        });

        dialog.show();
    }

    private void saveEditedProfile(
            ProxyProfile profile,
            String editedConfig,
            androidx.appcompat.app.AlertDialog dialog) {
        if (profile == null || editedConfig == null || editedConfig.trim().isEmpty()) {
            Toast.makeText(this, "配置内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        final String normalizedConfig = editedConfig.replace("\r\n", "\n").replace('\r', '\n').trim() + "\n";
        try {
            if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
                profile.setWireGuardConfig(WireGuardConfigParser.parse(normalizedConfig));
                profile.setOpenVpnConfig(null);
            } else {
                profile.setOpenVpnConfig(OpenVpnConfigParser.parse(normalizedConfig));
                profile.setWireGuardConfig(null);
            }
        } catch (Exception e) {
            Toast.makeText(this, "配置格式错误，未保存： " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        profile.setRawConfig(normalizedConfig);
        if (profile.getId().equals(repository.getActiveProfileId()) && isPortManuallyOverridden) {
            isPortManuallyOverridden = false;
            int configuredPort = 0;
            if (profile.getProtocolType() == ProtocolType.WIREGUARD && profile.getWireGuardConfig() != null) {
                configuredPort = profile.getWireGuardConfig().getEndpointPort();
            } else if (profile.getProtocolType() == ProtocolType.OPENVPN && profile.getOpenVpnConfig() != null) {
                configuredPort = profile.getOpenVpnConfig().getRemotePort();
            }
            if (configuredPort > 0) currentSelectedPort = configuredPort;
            if (btnNodePortSelect != null) btnNodePortSelect.setText(currentSelectedPort + " ▾");
        }
        repository.updateProfile(profile);
        refreshProfiles();
        renderNodeCards();

        PresetPortItem updatedPort = null;
        for (PresetPortItem port : PresetRegionManager.getInstance().getAllPorts()) {
            if (profile.getId().equals(port.getProfileId())) {
                updatedPort = port;
                break;
            }
        }

        int detectedPort = updatedPort != null ? updatedPort.getPort() : 0;
        String protocolLabel = profile.getProtocolType() == ProtocolType.WIREGUARD ? "WG" : "OVPN";
        String endpoint = profile.getEndpointDisplay();

        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }

        boolean isActive = profile.getId().equals(repository.getActiveProfileId());
        ConnectionState state = UnifiedVpnService.getCurrentState();
        if (isActive && (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING)) {
            Toast.makeText(this,
                    "配置已保存，检测到 " + protocolLabel + " 端口 " + detectedPort + "，正在按新配置重连",
                    Toast.LENGTH_LONG).show();
            Intent stopIntent = new Intent(this, UnifiedVpnService.class);
            stopIntent.setAction(UnifiedVpnService.ACTION_STOP_VPN);
            startService(stopIntent);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing()
                        && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    startVpnService();
                }
            }, 800);
        } else {
            Toast.makeText(this,
                    "配置已保存，已自动识别 " + protocolLabel + " 端口 " + detectedPort
                            + (endpoint.isEmpty() ? "" : " · " + endpoint),
                    Toast.LENGTH_LONG).show();
        }

        LogManager.log("MainActivity",
                "编辑配置已保存: " + profile.getName()
                        + " -> " + protocolLabel + " port=" + detectedPort);
    }

    private void deleteNodeConfirm(PresetPortItem item) {
        if (item == null || item.getProfileId() == null) {
            Toast.makeText(this, "该节点没有可编辑的配置文件", Toast.LENGTH_SHORT).show();
            return;
        }
        ProxyProfile profile = repository.getProfileById(item.getProfileId());
        if (profile == null) {
            Toast.makeText(this, "无法读取节点配置", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("删除节点")
                .setMessage("确定删除节点「" + profile.getName() + "」吗？\n删除后无法恢复。")
                .setPositiveButton("删除", (d, which) -> {
                    ProxyProfile running = UnifiedVpnService.getCurrentProfile();
                    ConnectionState state = UnifiedVpnService.getCurrentState();
                    if (running != null && item.getProfileId().equals(running.getId())
                            && state != ConnectionState.DISCONNECTED) {
                        Intent stopIntent = new Intent(this, UnifiedVpnService.class);
                        stopIntent.setAction(UnifiedVpnService.ACTION_STOP_VPN);
                        startService(stopIntent);
                    }
                    repository.deleteProfile(item.getProfileId());
                    Toast.makeText(this, "已删除节点: " + profile.getName(), Toast.LENGTH_SHORT).show();
                    LogManager.log("MainActivity", "删除节点: " + profile.getName());
                    refreshProfiles();
                    renderNodeCards();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void updateNodeLatencyDisplay(PresetPortItem item, View btnPing, View dotLatency, TextView tvLatency) {
        if (item.isTesting()) {
            tvLatency.setText("...");
            tvLatency.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            btnPing.setBackgroundResource(R.drawable.bg_latency_yellow);
            dotLatency.setBackgroundResource(R.drawable.bg_dot_yellow);
            return;
        }

        Long lat = item.getLatencyMs();
        if (lat == null) {
            tvLatency.setText("-- ms");
            tvLatency.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnPing.setBackgroundResource(R.drawable.bg_latency_yellow);
            dotLatency.setBackgroundResource(R.drawable.bg_dot_yellow);
        } else if (lat <= 0) {
            tvLatency.setText("未响应");
            tvLatency.setTextColor(ContextCompat.getColor(this, R.color.status_red));
            btnPing.setBackgroundResource(R.drawable.bg_latency_red);
            dotLatency.setBackgroundResource(R.drawable.bg_dot_red);
        } else {
            boolean genericUdp = "UDP".equalsIgnoreCase(item.getTransport())
                    && item.getPort() != 53 && item.getPort() != 123;
            String displayText = (genericUdp ? "~" : "") + lat + "ms";
            tvLatency.setText(displayText);
            tvLatency.setContentDescription(genericUdp
                    ? "主机响应延迟，不代表 UDP 服务端口可用"
                    : "节点响应延迟");
            if (lat < 60) {
                tvLatency.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                btnPing.setBackgroundResource(R.drawable.bg_latency_green);
                dotLatency.setBackgroundResource(R.drawable.bg_dot_green);
            } else if (lat < 140) {
                tvLatency.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
                btnPing.setBackgroundResource(R.drawable.bg_latency_yellow);
                dotLatency.setBackgroundResource(R.drawable.bg_dot_yellow);
            } else {
                tvLatency.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                btnPing.setBackgroundResource(R.drawable.bg_latency_red);
                dotLatency.setBackgroundResource(R.drawable.bg_dot_red);
            }
        }
    }

    private void scheduleLatencyUiUpdate(PresetPortItem item) {
        if (item == null) return;
        pendingLatencyUiUpdates.add(item.getUniqueId());
        latencyUiHandler.removeCallbacks(latencyUiRunnable);
        latencyUiHandler.postDelayed(latencyUiRunnable, 100);
    }

    private void testSinglePortLatency(PresetPortItem item, TextView tvLatency, View dotLatency, View btnPing) {
        if (item == null || item.isTesting()) return;
        tvLatency.setText("...");
        tvLatency.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        btnPing.setBackgroundResource(R.drawable.bg_latency_yellow);
        dotLatency.setBackgroundResource(R.drawable.bg_dot_yellow);

        PortSpeedTester.testSinglePort(item, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem it, long latencyMs) {
                scheduleLatencyUiUpdate(it);
                runOnUiThread(() -> {
                    boolean genericUdp = "UDP".equalsIgnoreCase(it.getTransport())
                            && it.getPort() != 53 && it.getPort() != 123;
                    String message = latencyMs > 0
                            ? it.getEffectiveChineseName() + " [:" + it.getPort() + "] "
                                + (genericUdp ? "主机响应延迟（不代表 UDP 端口可用）: " : "节点响应延迟: ")
                                + latencyMs + " ms"
                            : it.getEffectiveChineseName() + " [:" + it.getPort() + "] 未收到有效响应";
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {}
        });
    }

    private void testAllPortsSpeed() {
        List<PresetPortItem> all = getVisibleNodePorts();
        if (all.isEmpty()) {
            Toast.makeText(this, "当前协议或搜索条件下暂无可测速节点", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnTestAllSpeeds != null) {
            btnTestAllSpeeds.setEnabled(false);
            btnTestAllSpeeds.setText("⏳ 测速中");
        }
        Toast.makeText(this, "正在并发测速 " + all.size() + " 组节点...", Toast.LENGTH_SHORT).show();
        LogManager.log("MainActivity", "启动节点并发测速 (" + all.size() + " 组)...");

        for (PresetPortItem item : all) {
            setPortViewsTesting(item.getUniqueId());
        }

        PortSpeedTester.testAllPorts(all, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem item, long latencyMs) {
                scheduleLatencyUiUpdate(item);
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {
                runOnUiThread(() -> {
                    if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        return;
                    }
                    latencyUiHandler.removeCallbacks(latencyUiRunnable);
                    latencyUiRunnable.run();
                    if (btnTestAllSpeeds != null) {
                        btnTestAllSpeeds.setEnabled(true);
                        btnTestAllSpeeds.setText("⟳ 测速");
                    }
                    Toast.makeText(MainActivity.this, "全部测速完成！响应: " + successCount + " / " + totalCount, Toast.LENGTH_SHORT).show();
                    LogManager.log("MainActivity", "节点全量并发测速完毕: 响应 " + successCount + " / " + totalCount);
                    renderNodeCards();
                    updateActiveProfileUi();
                });
            }
        });
    }


    private void refreshPresetPortViews() {
        setupPresetRegions();
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private void updatePortLatencyBadge(TextView badgeView, PresetPortItem item) {
        if (item.isTesting()) {
            badgeView.setText("测速中...");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            return;
        }

        Long latency = item.getLatencyMs();
        if (latency == null) {
            badgeView.setText("测速");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else if (latency > 0) {
            badgeView.setText(latency + " ms");
            if (latency < 60) {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            } else if (latency <= 150) {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
            } else {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_red));
            }
        } else {
            badgeView.setText("超时");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_red));
        }
    }

    private PresetPortItem findActivePresetPort(ProxyProfile active) {
        if (active == null) return null;
        int activePort = 0;
        String activeTransport = "UDP";
        if (active.getProtocolType() == ProtocolType.WIREGUARD) {
            if (active.getWireGuardConfig() == null && active.getRawConfig() != null) {
                try {
                    active.setWireGuardConfig(WireGuardConfigParser.parse(active.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (active.getWireGuardConfig() != null) {
                activePort = active.getWireGuardConfig().getEndpointPort();
                activeTransport = "UDP";
            }
        } else if (active.getProtocolType() == ProtocolType.OPENVPN) {
            if (active.getOpenVpnConfig() == null && active.getRawConfig() != null) {
                try {
                    active.setOpenVpnConfig(OpenVpnConfigParser.parse(active.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (active.getOpenVpnConfig() != null) {
                activePort = active.getOpenVpnConfig().getRemotePort();
                if (active.getOpenVpnConfig().getProtocol() != null && !active.getOpenVpnConfig().getProtocol().trim().isEmpty()) {
                    activeTransport = active.getOpenVpnConfig().getProtocol().trim().toUpperCase(Locale.ROOT);
                }
            }
        }

        for (PresetPortItem item : PresetRegionManager.getInstance().getAllPorts()) {
            if (item.getProfileId() != null && (item.getProfileId().equals(active.getId()) || item.getUniqueId().equals(active.getId()))) {
                return item;
            }
        }
        return null;
    }


    // Split Tunneling / Routing Setup
    private void setupRoutingPage() {
        tvRoutingSummary = findViewById(R.id.tv_routing_summary);
        btnModeGlobal = findViewById(R.id.btn_mode_global);
        btnModeBypass = findViewById(R.id.btn_mode_bypass);
        btnModeAllow = findViewById(R.id.btn_mode_allow);
        tvModeDesc = findViewById(R.id.tv_mode_desc);
        etRoutingSearch = findViewById(R.id.et_routing_search);
        cbFilterSystemApps = findViewById(R.id.cb_filter_system_apps);
        pbRoutingLoading = findViewById(R.id.pb_routing_loading);
        rvRoutingApps = findViewById(R.id.rv_routing_apps);

        rvRoutingApps.setLayoutManager(new LinearLayoutManager(this));
        routingAppAdapter = new RoutingAppAdapter(this, selectedCount -> {
            tvRoutingSummary.setText("已勾选 " + selectedCount + " 个应用");
        });
        rvRoutingApps.setAdapter(routingAppAdapter);

        // Mode switchers
        btnModeGlobal.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_GLOBAL));
        btnModeBypass.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_BYPASS));
        btnModeAllow.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_ALLOW));

        updateRoutingModeUi(SplitTunnelManager.getRoutingMode(this));

        // Search watcher
        etRoutingSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                routingAppAdapter.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Filter system apps toggle
        cbFilterSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            routingAppAdapter.setIncludeSystem(isChecked);
        });
    }

    private void setRoutingMode(String mode) {
        SplitTunnelManager.setRoutingMode(this, mode);
        updateRoutingModeUi(mode);
        ConnectionState state = UnifiedVpnService.getCurrentState();
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            Toast.makeText(this, "分流规则已保存，断开并重新连接后生效", Toast.LENGTH_LONG).show();
        }
        LogManager.log("MainActivity", "分流规则已更新为: " + mode);
    }

    private void updateRoutingModeUi(String mode) {
        int activeBg = ContextCompat.getColor(this, R.color.brand_primary);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveBg = ContextCompat.getColor(this, R.color.surface_inner);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        btnModeGlobal.setBackgroundColor(SplitTunnelManager.MODE_GLOBAL.equals(mode) ? activeBg : inactiveBg);
        btnModeGlobal.setTextColor(SplitTunnelManager.MODE_GLOBAL.equals(mode) ? activeText : inactiveText);

        btnModeBypass.setBackgroundColor(SplitTunnelManager.MODE_BYPASS.equals(mode) ? activeBg : inactiveBg);
        btnModeBypass.setTextColor(SplitTunnelManager.MODE_BYPASS.equals(mode) ? activeText : inactiveText);

        btnModeAllow.setBackgroundColor(SplitTunnelManager.MODE_ALLOW.equals(mode) ? activeBg : inactiveBg);
        btnModeAllow.setTextColor(SplitTunnelManager.MODE_ALLOW.equals(mode) ? activeText : inactiveText);

        if (SplitTunnelManager.MODE_GLOBAL.equals(mode)) {
            tvModeDesc.setText("💡 全局代理模式：所有应用流量均通过 VPN 加密通道中转。");
        } else if (SplitTunnelManager.MODE_BYPASS.equals(mode)) {
            tvModeDesc.setText("💡 绕过选中模式：勾选的应用直接走本地网络，未勾选的应用通过代理加密通道。");
        } else {
            tvModeDesc.setText("💡 仅代理选中模式：只有勾选的应用走代理网络，其余所有应用直接走本地网络。");
        }
    }

    private void loadInstalledApplications() {
        pbRoutingLoading.setVisibility(View.VISIBLE);
        rvRoutingApps.setVisibility(View.GONE);

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(0);
            Set<String> selectedPackages = SplitTunnelManager.getSelectedPackages(this);

            List<AppItem> list = new ArrayList<>();
            for (PackageInfo pi : installed) {
                if (pi.applicationInfo == null) continue;
                String pkg = pi.packageName;
                if (pkg.equals(getPackageName())) continue; // Skip self

                String appName = pi.applicationInfo.loadLabel(pm).toString();
                Drawable icon = pi.applicationInfo.loadIcon(pm);
                boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isSelected = selectedPackages.contains(pkg);

                list.add(new AppItem(appName, pkg, icon, isSystem, isSelected));
            }

            // Sort: User apps first alphabetically, then system apps
            Collections.sort(list, (a, b) -> {
                if (a.isSystem() != b.isSystem()) {
                    return a.isSystem() ? 1 : -1;
                }
                return a.getAppName().compareToIgnoreCase(b.getAppName());
            });

            runOnUiThread(() -> {
                routingAppAdapter.setAppList(list);
                pbRoutingLoading.setVisibility(View.GONE);
                rvRoutingApps.setVisibility(View.VISIBLE);
                appsLoaded = true;
                tvRoutingSummary.setText("已勾选 " + routingAppAdapter.getSelectedCount() + " 个应用");
            });
        }).start();
    }

    // IP Pureness WebView Setup
    private void setupIppureWebView() {
        pbIppureWeb = findViewById(R.id.pb_ippure_web);
        swipeRefreshIppure = findViewById(R.id.swipe_refresh_ippure);
        webviewIppure = findViewById(R.id.webview_ippure);

        WebSettings settings = webviewIppure.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        String customUa = settings.getUserAgentString();
        if (customUa != null && customUa.contains("; wv")) {
            settings.setUserAgentString(customUa.replace("; wv", ""));
        }

        webviewIppure.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshIppure.setRefreshing(false);
                pbIppureWeb.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view,
                                        android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request == null || request.isForMainFrame()) {
                    swipeRefreshIppure.setRefreshing(false);
                    pbIppureWeb.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this,
                            "IPPure 页面加载失败，请检查网络连接",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        webviewIppure.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    pbIppureWeb.setVisibility(View.VISIBLE);
                    pbIppureWeb.setProgress(newProgress);
                } else {
                    pbIppureWeb.setVisibility(View.GONE);
                }
            }
        });

        swipeRefreshIppure.setOnRefreshListener(() -> webviewIppure.reload());

        findViewById(R.id.btn_refresh_ippure).setOnClickListener(v -> {
            pbIppureWeb.setVisibility(View.VISIBLE);
            webviewIppure.reload();
        });

        findViewById(R.id.btn_open_external_ippure).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ippure.com/"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "打开浏览器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadIppurePage() {
        ippureWebLoaded = true;
        pbIppureWeb.setVisibility(View.VISIBLE);
        webviewIppure.loadUrl("https://ippure.com/");
    }

    private void setupLaunchers() {
        vpnPrepareLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        startVpnService();
                    } else {
                        Toast.makeText(this, "用户拒绝了 VPN 授权", Toast.LENGTH_SHORT).show();
                        LogManager.log("MainActivity", "VPN 授权未通过");
                    }
                }
        );

        doubleHopPrepareLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        startDoubleHopService();
                    } else {
                        pendingDoubleHopConfig = null;
                        pendingDoubleHopProfile = null;
                        Toast.makeText(this, "用户拒绝了双跳 VPN 授权", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleImportedUri
        );

        batchFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                this::handleBatchImport
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        LogManager.log("MainActivity", "通知权限被拒绝，前台通知可能无法正常显示");
                    }
                }
        );
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void handleImportedUri(Uri uri) {
        if (uri == null) return;

        final String previousActiveId = repository.getActiveProfileId();
        Toast.makeText(this, "正在读取并解析配置…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String filename = deriveFilename(uri);

                StringBuilder sb = new StringBuilder();
                String content = readUriToString(uri);

                ProxyProfile profile = repository.autoImport(filename, content);

                String endpointHost = "";
                if (profile.getProtocolType() == ProtocolType.WIREGUARD
                        && profile.getWireGuardConfig() != null) {
                    endpointHost = profile.getWireGuardConfig().getEndpointHost();
                } else if (profile.getProtocolType() == ProtocolType.OPENVPN
                        && profile.getOpenVpnConfig() != null) {
                    endpointHost = profile.getOpenVpnConfig().getRemoteHost();
                }
                final String hostForGeo = endpointHost;

                runOnUiThread(() -> {
                    refreshProfiles();
                    if (previousActiveId == null) {
                        activeProtocol = profile.getProtocolType() == ProtocolType.OPENVPN
                                ? "OPENVPN" : "WIREGUARD";
                    }
                    selectProtocol(activeProtocol);
                    updateActiveProfileUi();
                    Toast.makeText(MainActivity.this,
                            "配置已导入，正在识别节点位置",
                            Toast.LENGTH_SHORT).show();
                });

                if (!hostForGeo.isEmpty()) {
                    IpGeoResolver.resolveAsync(hostForGeo, new IpGeoResolver.Callback() {
                        @Override
                        public void onResolved(IpGeoResolver.GeoInfo geo) {
                            runOnUiThread(() -> {
                                profile.setName(RegionDetector.normalizeDisplayName(geo.getDisplayName()));
                                if (profile.getName().isEmpty()) {
                                    profile.setName("导入节点");
                                }
                                repository.updateProfile(profile);

                                // Restore the previously selected node only when it still exists.
                                String currentActiveId = repository.getActiveProfileId();
                                if (previousActiveId != null
                                        && repository.getProfileById(previousActiveId) != null
                                        && profile.getId().equals(currentActiveId)) {
                                    repository.setActiveProfileId(previousActiveId);
                                }

                                refreshProfiles();
                                selectProtocol(activeProtocol);
                                Toast.makeText(MainActivity.this,
                                        "已识别节点位置: " + profile.getName(),
                                        Toast.LENGTH_SHORT).show();
                                LogManager.log("MainActivity",
                                        "导入节点 GeoIP: " + geo.getIp() + " -> " + profile.getName());
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> {
                                String currentActiveId = repository.getActiveProfileId();
                                if (previousActiveId != null
                                        && repository.getProfileById(previousActiveId) != null
                                        && profile.getId().equals(currentActiveId)) {
                                    repository.setActiveProfileId(previousActiveId);
                                }
                                refreshProfiles();
                                selectProtocol(activeProtocol);
                                Toast.makeText(MainActivity.this,
                                        "配置已导入，节点位置识别失败，已保留原名称",
                                        Toast.LENGTH_SHORT).show();
                                LogManager.log("MainActivity",
                                        "GeoIP 识别失败: " + error.getMessage());
                            });
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (previousActiveId != null
                                && repository.getProfileById(previousActiveId) != null) {
                            repository.setActiveProfileId(previousActiveId);
                        }
                        refreshProfiles();
                        selectProtocol(activeProtocol);
                    });
                }

                LogManager.log("MainActivity",
                        "成功导入配置: " + hostForGeo + " [" + profile.getProtocolType() + "]");
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "导入失败: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
                LogManager.log("MainActivity", "导入失败: " + e.getMessage());
            }
        }, "ProfileImportWorker").start();
    }

    /**
     * Derives a display-friendly filename from a document URI, stripped of its
     * .conf/.ovpn extension and normalized for geo location.
     */
    private String deriveFilename(Uri uri) {
        String filename = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        filename = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (filename == null || filename.isEmpty()) {
            filename = uri.getLastPathSegment();
        }
        if (filename != null && filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf('/') + 1);
        }
        if (filename == null || filename.trim().isEmpty()) {
            filename = "导入节点";
        }
        if (filename.matches("(?i).+\\.(conf|ovpn)$")) {
            filename = filename.substring(0, filename.lastIndexOf('.'));
        }
        filename = RegionDetector.normalizeDisplayName(filename);
        if (filename.isEmpty()) {
            filename = "导入节点";
        }
        return filename;
    }

    /**
     * Reads the full text content of a document URI as UTF-8.
     */
    private String readUriToString(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalArgumentException("无法读取配置文件");
        }
        try (InputStream is = input;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Imports multiple configuration files selected via the batch picker.
     * Each file is parsed independently; the UI is refreshed once all files
     * have been processed, with a summary of successes and failures.
     */
    private void handleBatchImport(List<Uri> result) {
        if (result == null || result.isEmpty()) return;

        Toast.makeText(this, "正在批量导入 " + result.size() + " 个配置文件…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            int successCount = 0;
        int failCount = 0;
        List<String> failures = new ArrayList<>();

        for (Uri uri : result) {
            if (uri == null) {
                failCount++;
                continue;
            }
            try {
                ProxyProfile profile = importFromUri(uri);
                if (profile == null) {
                    failCount++;
                    failures.add("无法识别协议类型");
                } else {
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(errorMsg);
                LogManager.error("MainActivity", "批量导入文件失败: " + errorMsg);
            }
        }

        final int finalSuccess = successCount;
        final int finalFail = failCount;
        runOnUiThread(() -> {
            refreshProfiles();
            if (finalFail > 0) {
                StringBuilder msg = new StringBuilder("批量导入完成：")
                        .append(finalSuccess).append(" 个成功、")
                        .append(finalFail).append(" 个失败");
                if (finalFail == 1) {
                    msg.append("：").append(failures.get(0));
                }
                Toast.makeText(MainActivity.this, msg.toString(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this,
                        "批量导入完成：" + finalSuccess + " 个配置文件已全部导入",
                        Toast.LENGTH_LONG).show();
            }
            LogManager.log("MainActivity",
                    "批量导入完成: 成功=" + finalSuccess + ", 失败=" + finalFail);
        });
        }, "BatchImportWorker").start();
    }

    /**
     * Reads a single URI and imports it, returning the created profile or null.
     */
    private ProxyProfile importFromUri(Uri uri) throws Exception {
        String filename = deriveFilename(uri);
        String content = readUriToString(uri);
        ProxyProfile profile = repository.autoImport(filename, content);
        if (profile == null) {
            throw new IllegalArgumentException("无法识别配置协议类型");
        }
        return profile;
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                handleImportedUri(uri);
            }
        }
    }



    private void showDoubleHopDialog() {
        ConnectionState state = UnifiedVpnService.getCurrentState();
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
                || lastConnectionState == ConnectionState.CONNECTED
                || lastConnectionState == ConnectionState.CONNECTING) {
            Toast.makeText(this, "请先断开当前连接，再启动双跳", Toast.LENGTH_LONG).show();
            return;
        }

        List<ProxyProfile> profiles = repository.getProfiles();
        if (profiles.size() < 2) {
            Toast.makeText(this, "至少导入两个节点配置后才能使用双跳", Toast.LENGTH_LONG).show();
            switchTab(1);
            return;
        }

        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            ProxyProfile profile = profiles.get(i);
            String protocol = profile.getProtocolType() == null
                    ? "未知协议" : profile.getProtocolType().name();
            labels[i] = profile.getName() + "  [" + protocol + "]";
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);

        TextView firstLabel = new TextView(this);
        firstLabel.setText("第一跳：手机先连接的节点");
        firstLabel.setTextSize(14);
        content.addView(firstLabel);

        Spinner firstSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firstSpinner.setAdapter(adapter);
        content.addView(firstSpinner);

        TextView secondLabel = new TextView(this);
        secondLabel.setText("第二跳：最终出口节点");
        secondLabel.setTextSize(14);
        LinearLayout.LayoutParams secondLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondLabelParams.topMargin = padding / 2;
        content.addView(secondLabel, secondLabelParams);

        Spinner secondSpinner = new Spinner(this);
        ArrayAdapter<String> secondAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        secondAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        secondSpinner.setAdapter(secondAdapter);
        secondSpinner.setSelection(1);
        content.addView(secondSpinner);

        TextView hint = new TextView(this);
        hint.setText("第二跳的服务器连接会经第一跳传输，系统流量统一从第二跳出站。双跳模式使用独立隧道内核。");
        hint.setTextSize(12);
        hint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = padding / 2;
        content.addView(hint, hintParams);

        new MaterialAlertDialogBuilder(this)
                .setTitle("配置双跳连接")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("连接双跳", (dialog, which) -> {
                    int firstIndex = firstSpinner.getSelectedItemPosition();
                    int secondIndex = secondSpinner.getSelectedItemPosition();
                    if (firstIndex < 0 || secondIndex < 0 || firstIndex >= profiles.size()
                            || secondIndex >= profiles.size()) {
                        Toast.makeText(this, "节点选择无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ProxyProfile first = copyProfile(profiles.get(firstIndex));
                    ProxyProfile second = copyProfile(profiles.get(secondIndex));
                    if (first.getId() != null && first.getId().equals(second.getId())) {
                        Toast.makeText(this, "第一跳和第二跳不能使用同一个节点", Toast.LENGTH_LONG).show();
                        return;
                    }
                    CredentialManager credentials = CredentialManager.getInstance();
                    boolean needCredentials = DoubleHopConfigBuilder.requiresCredentials(first)
                            || DoubleHopConfigBuilder.requiresCredentials(second);
                    if (needCredentials && (credentials == null || !credentials.hasCredentials())) {
                        showDoubleHopCredentialsDialog(first, second);
                    } else {
                        startDoubleHopConnection(first, second);
                    }
                })
                .show();
    }

    private void showDoubleHopCredentialsDialog(ProxyProfile first, ProxyProfile second) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding / 2, padding, 0);

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setHint("OpenVPN 用户名");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        fields.addView(username);

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("OpenVPN 密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(password);

        CheckBox saveCredentials = new CheckBox(this);
        saveCredentials.setText("保存账号密码");
        fields.addView(saveCredentials);

        new MaterialAlertDialogBuilder(this)
                .setTitle("双跳的 OpenVPN 凭据")
                .setMessage("当前双跳共用应用内保存的 OpenVPN 账号密码。")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> {
                    String user = username.getText() == null ? "" : username.getText().toString().trim();
                    String pass = password.getText() == null ? "" : password.getText().toString();
                    if (user.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(this, "用户名和密码不能为空", Toast.LENGTH_LONG).show();
                        return;
                    }
                    CredentialManager manager = CredentialManager.getInstance();
                    if (manager != null) {
                        if (saveCredentials.isChecked()) {
                            manager.saveCredentials(user, pass);
                        } else {
                            manager.setTemporaryCredentials(user, pass);
                        }
                    }
                    startDoubleHopConnection(first, second);
                })
                .show();
    }

    private void startDoubleHopConnection(ProxyProfile first, ProxyProfile second) {
        try {
            CredentialManager manager = CredentialManager.getInstance();
            String username = manager != null ? manager.getUsername() : "";
            String password = manager != null ? manager.getPassword() : "";
            pendingDoubleHopConfig = DoubleHopConfigBuilder.build(first, second, username, password);
            pendingDoubleHopProfile = copyProfile(second);
            pendingDoubleHopFirstName = first.getName();
            pendingDoubleHopSecondName = second.getName();

            Intent prepareIntent = VpnService.prepare(this);
            if (prepareIntent != null) {
                doubleHopPrepareLauncher.launch(prepareIntent);
            } else {
                startDoubleHopService();
            }
        } catch (Exception e) {
            LogManager.error("MainActivity", "双跳配置生成失败：" + e.getMessage());
            Toast.makeText(this, "双跳配置失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startDoubleHopService() {
        if (pendingDoubleHopConfig == null || pendingDoubleHopProfile == null) {
            Toast.makeText(this, "双跳配置已失效，请重新选择节点", Toast.LENGTH_LONG).show();
            return;
        }

        repository.setActiveProfileId(pendingDoubleHopProfile.getId());
        Intent intent = new Intent(this, DoubleHopVpnService.class);
        intent.setAction(DoubleHopVpnService.ACTION_START);
        intent.putExtra(DoubleHopVpnService.EXTRA_CONFIG, pendingDoubleHopConfig);
        intent.putExtra(DoubleHopVpnService.EXTRA_FIRST_NAME,
                pendingDoubleHopFirstName == null ? "第一跳" : pendingDoubleHopFirstName);
        intent.putExtra(DoubleHopVpnService.EXTRA_SECOND_NAME,
                pendingDoubleHopSecondName == null ? pendingDoubleHopProfile.getName() : pendingDoubleHopSecondName);
        intent.putExtra(UnifiedVpnService.EXTRA_PROFILE, pendingDoubleHopProfile);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent);
        } else {
            startService(intent);
        }
        LogManager.info("MainActivity", "请求启动双跳：" + pendingDoubleHopFirstName + " → " + pendingDoubleHopSecondName);
        pendingDoubleHopConfig = null;
        pendingDoubleHopProfile = null;
        pendingDoubleHopFirstName = null;
        pendingDoubleHopSecondName = null;
        updateActiveProfileUi();
    }

    private void toggleVpnConnection() {
        ConnectionState state = UnifiedVpnService.getCurrentState();
        ProxyProfile active = repository.getActiveProfile();

        boolean isConnectedOrConnecting = state == ConnectionState.CONNECTED
                || state == ConnectionState.CONNECTING
                || (state == ConnectionState.ERROR && UnifiedVpnService.isDoubleHopManaged())
                || lastConnectionState == ConnectionState.CONNECTED
                || lastConnectionState == ConnectionState.CONNECTING;

        if (isConnectedOrConnecting) {
            if (UnifiedVpnService.isDoubleHopManaged()) {
                // Send the stop request straight to the double-hop owner. The
                // shared service can be shutting down while libbox is blocked.
                LogManager.info("MainActivity", "用户请求断开双跳连接");
                Intent intent = new Intent(this, DoubleHopVpnService.class);
                intent.setAction(DoubleHopVpnService.ACTION_STOP);
                startService(intent);
            } else {
                // Existing single-hop path for WireGuard and OpenVPN.
                Intent intent = new Intent(this, UnifiedVpnService.class);
                intent.setAction(UnifiedVpnService.ACTION_STOP_VPN);
                startService(intent);
            }
        } else {
            if (active == null) {
                Toast.makeText(this, "请先选择或导入一个节点配置", Toast.LENGTH_SHORT).show();
                switchTab(1); // Jump to nodes
                return;
            }

            Intent prepareIntent = VpnService.prepare(this);
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent);
            } else {
                startVpnService();
            }
        }
    }

    private ProxyProfile copyProfile(ProxyProfile src) {
        if (src == null) return null;
        ProxyProfile copy = new ProxyProfile();
        copy.setId(src.getId());
        copy.setName(src.getName());
        copy.setProtocolType(src.getProtocolType());
        copy.setRawConfig(src.getRawConfig());
        copy.setCreatedAt(src.getCreatedAt());
        if (src.getProtocolType() == ProtocolType.WIREGUARD && src.getRawConfig() != null) {
            try {
                copy.setWireGuardConfig(WireGuardConfigParser.parse(src.getRawConfig()));
            } catch (Exception ignored) {
                copy.setWireGuardConfig(src.getWireGuardConfig());
            }
        }
        if (src.getProtocolType() == ProtocolType.OPENVPN && src.getRawConfig() != null) {
            try {
                copy.setOpenVpnConfig(OpenVpnConfigParser.parse(src.getRawConfig()));
            } catch (Exception ignored) {
                copy.setOpenVpnConfig(src.getOpenVpnConfig());
            }
        }
        return copy;
    }

    private void startVpnService() {
        ProxyProfile active = repository.getActiveProfile();
        if (active == null) return;

        ProxyProfile toStart = copyProfile(active);
        if (isPortManuallyOverridden && currentSelectedPort > 0) {
            applyPortToProfile(toStart, currentSelectedPort);
        }

        if (toStart.getProtocolType() == ProtocolType.OPENVPN
                && OpenVpnEngine.requiresUserPass(toStart.getRawConfig())) {
            CredentialManager credMgr = CredentialManager.getInstance();
            if (credMgr == null || !credMgr.hasCredentials()) {
                showOpenVpnCredentialsForConnection(toStart);
                return;
            }
        }

        Intent intent = new Intent(this, UnifiedVpnService.class);
        intent.setAction(UnifiedVpnService.ACTION_START_VPN);
        intent.putExtra(UnifiedVpnService.EXTRA_PROFILE, toStart);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && toStart.getProtocolType() != ProtocolType.OPENVPN) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateActiveProfileUi() {
        ProxyProfile active = repository.getActiveProfile();
        if (active != null) {
            String flag = "🌐";
            String regionName = "全球代理节点";
            PresetRegion matchedRegion = null;
            for (PresetRegion r : PresetRegionManager.getInstance().getRegions()) {
                if (r.getId().equals(active.getId())) {
                    matchedRegion = r;
                    break;
                }
            }
            if (matchedRegion != null) {
                flag = matchedRegion.getFlag();
                regionName = matchedRegion.getName();
            } else {
                RegionDetector.RegionInfo info = RegionDetector.detect(active.getName(), active.getEndpointDisplay(), active.getRawConfig());
                flag = info.getFlag();
                regionName = info.getName();
            }
            tvMiniGeoFlag.setText(flag);

            PresetPortItem activePort = findActivePresetPort(active);
            boolean activeIsWg = active.getProtocolType() == ProtocolType.WIREGUARD;
            tvMiniProtocolBadge.setText(activeIsWg ? "WG" : "OVPN");
            tvMiniProtocolBadge.setBackgroundResource(activeIsWg
                    ? R.drawable.bg_wireguard_badge : R.drawable.badge_rounded_bg);
            tvMiniProtocolBadge.setTextColor(ContextCompat.getColor(this,
                    activeIsWg ? R.color.wireguard_badge_text : R.color.brand_primary));
            String transportTag = activePort != null ? activePort.getTransport() : "UDP";
            int activePortNumber = (isPortManuallyOverridden && currentSelectedPort > 0)
                    ? currentSelectedPort
                    : (activePort != null ? activePort.getPort() : (activeIsWg ? 51820 : 1194));
            tvMiniGeoIp.setText(transportTag + " :" + activePortNumber);
            tvMiniGeoLocation.setText(regionName);

            if (activePort != null && activePort.getLatencyMs() != null) {
                long lat = activePort.getLatencyMs();
                if (lat > 0) {
                    boolean genericUdp = activePort != null
                            && "UDP".equalsIgnoreCase(activePort.getTransport())
                            && activePort.getPort() != 53 && activePort.getPort() != 123;
                    tvMiniPingBadge.setText((genericUdp ? "~" : "") + lat + " ms");
                    if (genericUdp) {
                        tvMiniPingBadge.setContentDescription("主机响应延迟，不代表 UDP 服务端口可用");
                    }
                    if (lat < 60) {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                    } else if (lat <= 150) {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
                    } else {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                    }
                } else {
                    tvMiniPingBadge.setText("超时");
                    tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                }
            } else {
                tvMiniPingBadge.setText("未测速");
                tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            }
        } else {
            tvMiniGeoFlag.setText("🌐");
            tvMiniGeoIp.setText("未选择配置");
            tvMiniProtocolBadge.setText("--");
            tvMiniGeoLocation.setText("请在节点库中选择或导入配置");
            tvMiniPingBadge.setText("-- ms");
            tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void updateConnectionUi(ConnectionState state) {
        updateConnectionUi(state, null);
    }

    private void updateConnectionUi(ConnectionState state, String errorMessage) {
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            lastErrorMessageDisplay = errorMessage.trim();
        } else if (state == ConnectionState.CONNECTED) {
            lastErrorMessageDisplay = null;
        }

        boolean justConnected = lastConnectionState != ConnectionState.CONNECTED
                && state == ConnectionState.CONNECTED;
        switch (state) {
            case CONNECTED:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connected);
                tvOrbIcon.setText("⚡");
                tvOrbStatusText.setText("已连接");
                tvProtectionBadge.setOnClickListener(v -> {
                    Toast.makeText(this, "正在重新检测外网出口与 IP…", Toast.LENGTH_SHORT).show();
                    probeEgressConnectivity();
                });
                if (waveformView != null) {
                    waveformView.setConnected(true);
                }

                if (!isTimerRunning) {
                    connectionStartTime = System.currentTimeMillis();
                    isTimerRunning = true;
                    timerHandler.post(timerRunnable);
                }

                if (justConnected || egressProbeStatus == null) {
                    probeEgressConnectivity();
                }

                ProxyProfile curProfile = repository.getActiveProfile();
                if (justConnected) {
                    resetTrafficSampling();
                    TrafficStatsManager trafficStats = TrafficStatsManager.getInstance();
                    if (curProfile != null && !trafficStats.isSessionActive()) {
                        String ep = curProfile.getEndpointDisplay();
                        String ch = getProtocolChannelDisplay(curProfile);
                        String tip = getVirtualTunDisplay(curProfile);
                        trafficStats.startSession(ep, ch, tip);
                    }
                }

                // If user visits IPPure tab, reload page once upon transitioning to CONNECTED
                if (lastConnectionState != ConnectionState.CONNECTED && ippureWebLoaded && webviewIppure != null) {
                    webviewIppure.reload();
                }
                break;

            case CONNECTING:
                // End the previous session on reconnect; CONNECTED will start the new one.
                TrafficStatsManager.getInstance().stopSession();
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connecting);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connecting);
                tvOrbIcon.setText("⏳");
                tvOrbStatusText.setText("连接中...");
                tvProtectionBadge.setText("正在建立安全隧道...");
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvProtectionBadge.setOnClickListener(null);
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                break;

            case DISCONNECTING:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connecting);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connecting);
                tvOrbIcon.setText("⏳");
                tvOrbStatusText.setText("断开中...");
                tvProtectionBadge.setText("正在终止安全隧道...");
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvProtectionBadge.setOnClickListener(null);
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                TrafficStatsManager.getInstance().stopSession();
                break;

            case ERROR:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_disconnected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_disconnected);
                tvOrbIcon.setText("⚠️");
                tvOrbStatusText.setText("连接失败");
                String displayReason = (lastErrorMessageDisplay != null) ? lastErrorMessageDisplay : "请检查节点配置与网络";
                tvProtectionBadge.setText("❌ " + displayReason + " (点击查看日志)");
                tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                tvProtectionBadge.setOnClickListener(v -> showLogConsoleBottomSheet());
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                resetTrafficBars();
                TrafficStatsManager.getInstance().stopSession();
                break;

            case DISCONNECTED:
            default:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_disconnected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_disconnected);
                tvOrbIcon.setText("⚡");
                tvOrbStatusText.setText("未连接");
                if (lastErrorMessageDisplay != null) {
                    tvProtectionBadge.setText("❌ " + lastErrorMessageDisplay + " (点击查看日志)");
                    tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                    tvProtectionBadge.setOnClickListener(v -> showLogConsoleBottomSheet());
                } else {
                    tvProtectionBadge.setText("⚠️ 代理未开启 · 点击能量环启动");
                    tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    tvProtectionBadge.setOnClickListener(null);
                }
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                resetTrafficBars();
                TrafficStatsManager.getInstance().stopSession();
                break;
        }
        lastConnectionState = state;
    }

    private void stopConnectionTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        egressProbeStatus = null;
        isProbingEgress.set(false);
    }

    private void onTrafficUpdate(long rxBytes, long txBytes) {
        long now = System.currentTimeMillis();
        double downSpeed = 0;
        double upSpeed = 0;
        if (lastTrafficTimestamp > 0 && now > lastTrafficTimestamp) {
            double seconds = (now - lastTrafficTimestamp) / 1000.0;
            downSpeed = Math.max(0, (rxBytes - lastRxBytes) / seconds);
            upSpeed = Math.max(0, (txBytes - lastTxBytes) / seconds);

            tvSpeedDown.setText(formatSpeedNumber(downSpeed));
            tvSpeedDownUnit.setText(formatSpeedUnit(downSpeed));
            tvSpeedUp.setText(formatSpeedNumber(upSpeed));
            tvSpeedUpUnit.setText(formatSpeedUnit(upSpeed));

            if (downSpeed > peakSpeedBytesPerSec) {
                peakSpeedBytesPerSec = downSpeed;
                tvPeakSpeed.setText("峰值: " + formatSpeed(peakSpeedBytesPerSec));
            }

            // Feed hardware-accelerated oscilloscope waveform view
            if (waveformView != null) {
                waveformView.addSpeedSample((float) downSpeed, (float) upSpeed);
            }
        }

        currentDownSpeed = downSpeed;
        currentUpSpeed = upSpeed;

        lastRxBytes = rxBytes;
        lastTxBytes = txBytes;
        lastTrafficTimestamp = now;

        tvTotalDown.setText(TrafficStatsManager.formatBytes(rxBytes));
        tvTotalUp.setText(TrafficStatsManager.formatBytes(txBytes));

        TrafficStatsManager.getInstance().updateSessionTraffic(rxBytes, txBytes, downSpeed, upSpeed);
    }

    private void resetTrafficSampling() {
        lastRxBytes = 0;
        lastTxBytes = 0;
        lastTrafficTimestamp = 0;
        peakSpeedBytesPerSec = 0;
        currentDownSpeed = 0;
        currentUpSpeed = 0;
        resetTrafficBars();
        if (tvPeakSpeed != null) {
            tvPeakSpeed.setText("峰值: 0.0 B/s");
        }
    }

    private void resetTrafficBars() {
        if (waveformView != null) {
            waveformView.reset();
        }
        tvSpeedDown.setText("0.0");
        tvSpeedDownUnit.setText("KB/s");
        tvSpeedUp.setText("0.0");
        tvSpeedUpUnit.setText("KB/s");
    }

    private String formatSpeedNumber(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format(Locale.getDefault(), "%.1f", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f", bytesPerSec / 1024.0);
        if (bytesPerSec < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f", bytesPerSec / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.1f", bytesPerSec / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatSpeedUnit(double bytesPerSec) {
        if (bytesPerSec < 1024) return "B/s";
        if (bytesPerSec < 1024 * 1024) return "KB/s";
        if (bytesPerSec < 1024 * 1024 * 1024) return "MB/s";
        return "GB/s";
    }

    private String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format(Locale.getDefault(), "%.1f B/s", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }

    private void refreshProfiles() {
        refreshPresetPortViews();
        updateActiveProfileUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UnifiedVpnService.ACTION_STATE_CHANGED);
        filter.addAction(UnifiedVpnService.ACTION_TRAFFIC_UPDATE);

        ContextCompat.registerReceiver(this, vpnStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        updateConnectionUi(UnifiedVpnService.getCurrentState());
        updateActiveProfileUi();
    }


    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(vpnStateReceiver);
        } catch (IllegalArgumentException ignored) {}
    }


    @Override
    protected void onDestroy() {
        stopConnectionTimer();
        searchHandler.removeCallbacks(searchRenderRunnable);
        latencyUiHandler.removeCallbacks(latencyUiRunnable);
        pendingLatencyUiUpdates.clear();

        // Fix: dismiss any open dialogs to prevent WindowLeaked crash on Activity destruction
        // (e.g. during screen rotation or when app is killed while a dialog is open).
        if (logConsoleDialog != null && logConsoleDialog.isShowing()) {
            logConsoleDialog.dismiss();
            logConsoleDialog = null;
        }

        // Fix: clear LogManager listener to release the WeakReference and prevent
        // potential dangling callbacks into a dead Activity.
        LogManager.setListener(null);
        logConsoleListener = null;

        if (webviewIppure != null) {
            if (webviewIppure.getParent() instanceof ViewGroup) {
                ((ViewGroup) webviewIppure.getParent()).removeView(webviewIppure);
            }
            webviewIppure.destroy();
            webviewIppure = null;
        }

        try {
            TrafficStatsManager.getInstance().flushPendingTraffic();
        } catch (Exception ignored) {}

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pageIppure.getVisibility() == View.VISIBLE && webviewIppure != null && webviewIppure.canGoBack()) {
            webviewIppure.goBack();
        } else if (pageNodes.getVisibility() == View.VISIBLE || pageRouting.getVisibility() == View.VISIBLE || pageIppure.getVisibility() == View.VISIBLE) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }

}
