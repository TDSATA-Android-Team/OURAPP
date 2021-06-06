package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ActivitySearch extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private final MyLog myLog = new MyLog("SearchTAG");
    private InputMethodManager inputMethodManager;
    private File historyCache;
    private List<String> histories = null;
    private final List<PossibleResult> possibleResults = new LinkedList<>();

    private View returnHome;
    private EditText inputSearch;
    private View search;
    private ViewGroup parent;
    private ListView searchHistory;
    private ListView searchResult;
    private View tipBlank;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Tools.setBlackWordOnStatus(activity);
        inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        historyCache = Tools.generateFileAtCache(activity, null, FixedValue.SearchCfg, true);
        if (historyCache.exists()) {
            String[] strings = Tools.readObjectFromFile(historyCache, String[].class);
            if (strings != null) {
                histories = new ArrayList<>(Arrays.asList(strings));
            }
        } else {
            try {
                if (!historyCache.createNewFile()) {
                    myLog.e("搜索历史缓存文件创建失败");
                }
            } catch (IOException e) {
                myLog.e("搜索历史缓存文件创建失败", e);
            }
        }
        if (histories == null) {
            histories = new ArrayList<>();
        }
        initView();
        myListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Tools.saveObjectAtFile(historyCache, histories);
    }

    @Override
    public void onBackPressed() {
        if (inputSearch.hasFocus()) {
            inputSearch.clearFocus();
        } else {
            super.onBackPressed();
        }
    }

    private void initView() {
        returnHome = findViewById(R.id.returnHome);
        inputSearch = findViewById(R.id.inputSearch);
        search = findViewById(R.id.search);
        parent = findViewById(R.id.parent);
        searchHistory = findViewById(R.id.searchHistory);
        searchResult = findViewById(R.id.searchResult);
        tipBlank = View.inflate(activity, R.layout.layout_tip_blank, null);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        tipBlank.setLayoutParams(params);
    }

    private void myListener() {
        returnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.finish();
            }
        });

        inputSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (histories.size() > 0) {
                        searchHistory.setVisibility(View.VISIBLE);
                        searchHistory.setAdapter(new SearchHistoryListAdapter());
                    }
                } else {
                    searchHistory.setVisibility(View.GONE);
                }
            }
        });

        inputSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    inputSearch();
                    return true;
                }
                return false;
            }
        });

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputSearch();
            }
        });

        searchHistory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String key = histories.get(position);
                if (position > 0) {
                    Collections.rotate(histories.subList(0, position + 1), 1);
                }
                inputSearch.setText(key);
                search(key);
            }
        });

        searchResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent toInfo = new Intent(activity, ActivityPersonalInformation.class);
                toInfo.putExtra(FixedValue.currentMember, possibleResults.get(position).member);
                startActivity(toInfo);
            }
        });
    }

    private void inputSearch() {
        String key = String.valueOf(inputSearch.getText());
        if (TextUtils.isEmpty(key)) {
            Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        search(key);
    }

    private void search(String key) {
        inputMethodManager.hideSoftInputFromWindow(inputSearch.getWindowToken(), 0);
        inputSearch.clearFocus();
        possibleResults.clear();
        for (Member member : Tools.memberList) {
            if (member.getName().contains(key) || member.getNumber().contains(key)) {
                possibleResults.add(new PossibleResult(member, PossibleResult.HIGH));
            } else if (member.getTeacher().contains(key) || member.getSubject().contains(key)) {
                possibleResults.add(new PossibleResult(member, PossibleResult.LOW));
            }
        }
        if (possibleResults.size() > 0) {
            if (!histories.contains(key)) {
                histories.add(0, key);
                if (histories.size() > 10) {
                    histories = histories.subList(0, 10);
                }
            }
            Collections.sort(possibleResults, new Comparator<PossibleResult>() {
                @Override
                public int compare(PossibleResult o1, PossibleResult o2) {
                    return Integer.compare(o1.level, o2.level);
                }
            });
            parent.removeView(tipBlank);
            searchResult.setAdapter(new SearchResultListAdapter(activity, possibleResults));
        } else {
            searchResult.setAdapter(null);
            if (parent.indexOfChild(tipBlank) < 0) {
                parent.addView(tipBlank);
            }
        }
    }

    private static class PossibleResult {
        private final Member member;
        private final int level;

        /**
         * 可能性较高.
         */
        private static final int HIGH = 0;

        /**
         * 可能性较低.
         */
        private static final int LOW = 1;

        private PossibleResult(Member member, int level) {
            this.member = member;
            this.level = level;
        }
    }

    private class SearchHistoryListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return histories.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(activity, R.layout.item_list_search_history, null);
                viewHolder = new ViewHolder();
                viewHolder.history = convertView.findViewById(R.id.history);
                viewHolder.delete = convertView.findViewById(R.id.delete);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            final String history = histories.get(position);
            viewHolder.history.setText(history);
            viewHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    histories.remove(history);
                    searchHistory.setAdapter(new SearchHistoryListAdapter());
                }
            });
            return convertView;
        }

        private class ViewHolder {
            private TextView history;
            private View delete;
        }
    }

    private static class SearchResultListAdapter extends BaseAdapter {
        private final Context context;
        private final List<PossibleResult> possibleResults;

        private SearchResultListAdapter(Context context, List<PossibleResult> possibleResults) {
            this.context = context;
            this.possibleResults = possibleResults;
        }

        @Override
        public int getCount() {
            return possibleResults.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(context, R.layout.item_list_search_result, null);
                viewHolder = new ViewHolder();
                viewHolder.name = convertView.findViewById(R.id.name);
                viewHolder.number = convertView.findViewById(R.id.number);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            PossibleResult possibleResult = possibleResults.get(position);
            viewHolder.name.setText(possibleResult.member.getName());
            viewHolder.number.setText(possibleResult.member.getNumber());
            return convertView;
        }

        private static class ViewHolder {
            private TextView name;
            private TextView number;
        }
    }
}