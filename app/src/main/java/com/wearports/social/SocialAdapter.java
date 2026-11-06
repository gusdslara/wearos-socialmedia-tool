package com.wearports.social;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SocialAdapter extends RecyclerView.Adapter<SocialAdapter.ViewHolder> {

    public interface OnSocialClickListener {
        void onSocialClick(SocialItem item);
    }

    public static class SocialItem {
        public final String name;
        public final String subtitle;
        public final String url;
        public final int iconRes;

        public SocialItem(String name, String subtitle, String url, int iconRes) {
            this.name = name;
            this.subtitle = subtitle;
            this.url = url;
            this.iconRes = iconRes;
        }
    }

    private final Context context;
    private final List<SocialItem> items;
    private final OnSocialClickListener listener;

    public SocialAdapter(Context context, List<SocialItem> items, OnSocialClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_social, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SocialItem item = items.get(position);
        holder.title.setText(item.name);
        holder.subtitle.setText(item.subtitle);
        holder.icon.setImageResource(item.iconRes);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSocialClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView icon;
        public final TextView title;
        public final TextView subtitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.img_icon);
            title = itemView.findViewById(R.id.txt_title);
            subtitle = itemView.findViewById(R.id.txt_subtitle);
        }
    }
}
