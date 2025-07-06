package org.libreoffice.ui;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import org.libreoffice.R;
import org.libreoffice.data.RecentFile;
import org.libreoffice.utils.FileUtilities;

import java.util.List;

class RecentFilesAdapter extends RecyclerView.Adapter<RecentFilesAdapter.ViewHolder> {

    private final LibreOfficeUIActivity mActivity;
    private final List<RecentFile> recentFiles;
    RecentFilesAdapter(LibreOfficeUIActivity activity, List<RecentFile> recentFiles) {
        this.mActivity = activity;
        this.recentFiles = recentFiles;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_files, parent, false);
        return new ViewHolder(item);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final RecentFile entry = recentFiles.get(position);
        holder.itemView.setOnClickListener(view -> mActivity.openDocument(entry.getUri()));
        final String filename = entry.getDisplayName();
        holder.textView.setText(filename);
        int compoundDrawableInt = switch (FileUtilities.getType(filename)) {
            case FileUtilities.CALC -> R.drawable.ic_calc;
            case FileUtilities.DRAWING -> R.drawable.ic_draw;
            case FileUtilities.IMPRESS -> R.drawable.ic_impress;
            case FileUtilities.PDF -> R.drawable.ic_pdf;
            default -> R.drawable.ic_writer;
        };
        holder.imageView.setImageDrawable(ContextCompat.getDrawable(mActivity, compoundDrawableInt));
    }

    @Override
    public int getItemCount() {
        return recentFiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView textView;
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(R.id.textView);
            this.imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
