package org.libreoffice.adapter;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.libreoffice.R;
import org.libreoffice.utils.ThumbnailCreator;
import org.libreoffice.data.DocumentPartView;

import java.util.List;

public class DocumentPartViewListAdapter extends ArrayAdapter<DocumentPartView> {

    private final Activity activity;
    private final ThumbnailCreator thumbnailCollector;

    public DocumentPartViewListAdapter(Activity activity, int resource, List<DocumentPartView> objects) {
        super(activity, resource, objects);
        this.activity = activity;
        this.thumbnailCollector = new ThumbnailCreator();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            view = layoutInflater.inflate(R.layout.document_part_list_layout, null);
        }

        DocumentPartView documentPartView = getItem(position);
        TextView textView = view.findViewById(R.id.text);
        textView.setText(documentPartView.partName);

        ImageView imageView = view.findViewById(R.id.image);
        thumbnailCollector.createThumbnail(position, imageView);

        return view;
    }
}


