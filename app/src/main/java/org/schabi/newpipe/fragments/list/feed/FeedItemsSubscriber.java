package org.schabi.newpipe.fragments.list.feed;

import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoListAdapter;

import java.util.List;

import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.MainActivity.DEBUG;

/*
 * Created by wojcik.online on 2018-01-22.
 */

final class FeedItemsSubscriber implements Subscriber<List<StreamInfoItem>>, Disposable {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final FeedFragment feedFragment;
    private final InfoListAdapter infoListAdapter;

    private Subscription thisSubscription;

    FeedItemsSubscriber(FeedFragment feedFragment, InfoListAdapter infoListAdapter) {
        this.feedFragment = feedFragment;
        this.infoListAdapter = infoListAdapter;
    }

    void requestNext() {
        if (thisSubscription != null) {
            thisSubscription.request(1);
        }
    }


    @Override
    public void onSubscribe(Subscription subscription) {
        thisSubscription = subscription;
        requestNext();
    }

    @Override
    public void onNext(List<StreamInfoItem> items) {
        for (StreamInfoItem item: items) {
            infoListAdapter.addInfoItem(item);
            feedFragment.setItemDisplayed(item);
        }

        feedFragment.setLoadingFinished();
    }

    @Override
    public void onError(Throwable t) {
        feedFragment.onError(t);
    }

    @Override
    public void onComplete() {
        if (DEBUG) Log.d(TAG, "onComplete() All items loaded");

        feedFragment.setAllItemsDisplayed(true);
        feedFragment.showListFooter(false);
        feedFragment.hideLoading();

        if (infoListAdapter.getItemsList().isEmpty()) {
            feedFragment.showEmptyState();
        }
    }

    @Override
    public void dispose() {
        if (thisSubscription != null) {
            thisSubscription.cancel();
            thisSubscription = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return thisSubscription == null;
    }
}
