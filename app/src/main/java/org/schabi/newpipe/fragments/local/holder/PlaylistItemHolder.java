package org.schabi.newpipe.fragments.local.holder;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.fragments.local.LocalItemBuilder;
import org.schabi.newpipe.util.ThemeHelper;

import java.text.DateFormat;

public abstract class PlaylistItemHolder extends LocalItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    private Context context;

    public PlaylistItemHolder(LocalItemBuilder infoItemBuilder,
                              int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        context = parent.getContext();

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        initDrawable();
    }

    private void initDrawable(){
        itemStreamCountView.setCompoundDrawablesWithIntrinsicBounds(null, ThemeHelper.getDrawableFromRes(context,R
                .drawable.ic_playlist_play_white_24dp),null,null);
    }

    public PlaylistItemHolder(LocalItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem, final DateFormat dateFormat) {
        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(localItem);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(localItem);
            }
            return true;
        });
    }
}
