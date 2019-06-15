package cn.nlifew.scrollcollapsinglayout.adapter;

import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;

public class ViewHolder extends RecyclerView.ViewHolder {

    public ViewHolder(View itemView) {
        super(itemView);
    }

    private SparseArray<View> mView;

    @SuppressWarnings("unchecked")
    public <T extends View> T getView(int id) {
        if (mView == null) {
            mView = new SparseArray<>();
        }
        View v = mView.get(id);
        if (v == null) {
            v = itemView.findViewById(id);
            mView.put(id, v);
        }
        return (T) v;
    }
}
