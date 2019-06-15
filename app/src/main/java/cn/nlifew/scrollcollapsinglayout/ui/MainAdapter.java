package cn.nlifew.scrollcollapsinglayout.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.TextView;

import cn.nlifew.scrollcollapsinglayout.adapter.ViewHolder;
import cn.nlifew.scrollcollapsinglayout.utils.Utils;

class MainAdapter extends RecyclerView.Adapter<ViewHolder> {

    MainAdapter(Context context) {
        mContext = context;
    }

    private Context mContext;

    @Override
    public int getItemCount() {
        return 50;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(mContext);

        int dp50 = Utils.dp2px(50);
        int dp10 = Utils.dp2px(10);

        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp50);

        params.topMargin = params.bottomMargin = dp10;

        tv.setLayoutParams(params);

        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TextView tv = (TextView) holder.itemView;
        tv.setText(Integer.toString(position));
    }
}
