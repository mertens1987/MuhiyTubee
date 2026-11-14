package org.schabi.newpipe.fragments.list.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import icepick.State;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * MuhiyTube home feed.
 * <p>
 * Shows a mixed set of videos (never live streams). Every refresh picks different random
 * topics, so the feed is different each time. In {@link #MODE_SHORTS} only short videos are
 * shown.
 */
public class HomeFeedFragment extends BaseListInfoFragment<InfoItem, SearchInfo> {
    public static final String MODE_HOME = "home";
    public static final String MODE_SHORTS = "shorts";

    private static final String YOUTUBE_URL = "https://www.youtube.com";
    private static final long SHORTS_MAX_DURATION_SECONDS = 180;
    private static final List<String> VIDEOS_FILTER = Collections.singletonList("videos");

    private static final List<String> HOME_TOPICS = Arrays.asList(
            "müzik", "komik videolar", "teknoloji", "oyun", "belgesel", "yemek tarifleri",
            "spor özetleri", "film fragmanları", "bilim", "seyahat vlog", "motivasyon",
            "hayvanlar", "araba", "tarih", "sanat", "diy yapımı", "gündem", "podcast",
            "ders anlatımı", "kitap", "fotoğrafçılık", "mizah", "deney", "uzay");

    private static final List<String> SHORTS_TOPICS = Arrays.asList(
            "komik", "müzik", "oyun", "yemek", "hayvanlar", "spor", "teknoloji", "dans",
            "sihir", "bilim", "araba", "güzel manzara", "motivasyon", "futbol");

    @State
    String mode = MODE_HOME;
    @State
    String pagingQuery = "";

    public static HomeFeedFragment getInstance(final String mode) {
        final HomeFeedFragment instance = new HomeFeedFragment();
        instance.mode = mode;
        instance.setInitialData(ServiceList.YouTube.getServiceId(), YOUTUBE_URL, "");
        return instance;
    }

    public HomeFeedFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    private boolean isShorts() {
        return MODE_SHORTS.equals(mode);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (serviceId < 0) {
            serviceId = ServiceList.YouTube.getServiceId();
        }
        if (url == null) {
            url = YOUTUBE_URL;
        }
        name = getString(isShorts() ? R.string.muhiy_shorts_title : R.string.muhiy_home_title);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<SearchInfo> loadResult(final boolean forceLoad) {
        final List<String> queries = pickQueries();
        pagingQuery = queries.get(0);

        final List<Single<SearchInfo>> searches = new ArrayList<>();
        for (final String query : queries) {
            searches.add(ExtractorHelper.searchFor(serviceId, query, VIDEOS_FILTER, "")
                    .subscribeOn(Schedulers.io()));
        }
        return Single.zip(searches, results -> mergeResults(results));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreSearchItems(
                        serviceId, pagingQuery, VIDEOS_FILTER, "", currentNextPage)
                .map(page -> new ListExtractor.InfoItemsPage<InfoItem>(
                        filterItems(page.getItems()), page.getNextPage(), page.getErrors()));
    }

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        super.handleResult(result);
        name = getString(isShorts() ? R.string.muhiy_shorts_title : R.string.muhiy_home_title);
        setTitle(name);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private List<String> pickQueries() {
        final List<String> topics = new ArrayList<>(isShorts() ? SHORTS_TOPICS : HOME_TOPICS);
        Collections.shuffle(topics);
        final List<String> queries = new ArrayList<>();
        final int count = isShorts() ? 2 : 3;
        for (int i = 0; i < count; i++) {
            final String topic = topics.get(i);
            queries.add(isShorts() ? topic + " #shorts" : topic);
        }
        return queries;
    }

    private SearchInfo mergeResults(final Object[] results) {
        final Map<String, InfoItem> merged = new LinkedHashMap<>();
        SearchInfo first = null;
        for (final Object result : results) {
            final SearchInfo info = (SearchInfo) result;
            if (first == null) {
                first = info;
            }
            for (final InfoItem item : filterItems(info.getRelatedItems())) {
                merged.put(item.getUrl(), item);
            }
        }
        final List<InfoItem> items = new ArrayList<>(merged.values());
        Collections.shuffle(items);
        first.setRelatedItems(items);
        return first;
    }

    /**
     * Removes live streams (and, in shorts mode, everything that is not a short video).
     */
    private List<InfoItem> filterItems(final List<? extends InfoItem> items) {
        final List<InfoItem> filtered = new ArrayList<>();
        for (final InfoItem item : items) {
            if (!(item instanceof StreamInfoItem)) {
                continue;
            }
            final StreamInfoItem stream = (StreamInfoItem) item;
            if (stream.getStreamType() == StreamType.LIVE_STREAM
                    || stream.getStreamType() == StreamType.AUDIO_LIVE_STREAM) {
                continue;
            }
            final long duration = stream.getDuration();
            if (isShorts() && (duration <= 0 || duration > SHORTS_MAX_DURATION_SECONDS)) {
                continue;
            }
            if (!isShorts() && duration > 0 && duration <= 60) {
                // keep the home feed free of shorts
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }
}
