package com.example.scparchive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,16,18), PANEL = Color.rgb(22,30,33);
    private static final int PANEL2 = Color.rgb(31,41,44), TEXT = Color.rgb(234,241,237);
    private static final int MUTED = Color.rgb(160,175,170), GREEN = Color.rgb(174,224,194);
    private static final int EDGE = Color.rgb(47,67,61);
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private ArchiveDb db;
    private SharedPreferences prefs;
    private LinearLayout root, resultList;
    private TextView progressView, countView;
    private String filter = "all", search = "", openSlug = null;
    private int visible = 40;
    private volatile boolean busy = false;
    private final FileImageLoader imageLoader = new FileImageLoader();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("archive_settings", MODE_PRIVATE);
        db = new ArchiveDb(this);
        makePage();
        label("LOADING LOCAL ARCHIVE", 16, GREEN, true);
        io.submit(() -> {
            if (db.count() == 0) {
                try (InputStream in = getAssets().open("catalog.json")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192]; int n;
                    while ((n = in.read(chunk)) != -1) out.write(chunk,0,n);
                    JSONObject rootData = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                    JSONObject items = rootData.optJSONObject("entries");
                    if (items != null) db.merge(items, false, false);
                } catch (Exception ex) { ui(() -> toast("Starter catalog unavailable. Use Sync Latest.")); }
            }
            ui(this::showHome);
        });
    }

    private boolean offline() { return prefs.getBoolean("offline", false); }
    private void ui(Runnable r) { runOnUiThread(r); }
    private int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke!=0) d.setStroke(dp(1), stroke);
        return d;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    private TextView text(String s, int size, int color, boolean strong) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1.05f);
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void gap(LinearLayout parent,int h) { View v = new View(this); parent.addView(v, lp(1,h)); }
    private TextView label(String s, int size, int color, boolean strong) {
        TextView t=text(s,size,color,strong); root.addView(t,lp(-1,-2)); return t;
    }
    private LinearLayout panel(LinearLayout parent) {
        LinearLayout box = vertical(); box.setPadding(dp(16),dp(16),dp(16),dp(16));
        box.setBackground(bg(PANEL,16,EDGE)); parent.addView(box,lp(-1,-2)); return box;
    }
    private Button button(String label, boolean filled, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextSize(13); b.setTextColor(filled?BG:GREEN);
        b.setBackground(bg(filled?GREEN:PANEL2,12,filled?0:EDGE));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(10),dp(4),dp(10),dp(4));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void makePage() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root=vertical(); root.setPadding(dp(18),dp(24),dp(18),dp(26));
        scroll.addView(root); setContentView(scroll);
    }
    private void toast(String msg) { Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); }
    private void header(String sub) {
        TextView pre = label("●   FOUNDATION DATABASE  /  " + sub.toUpperCase(),11,GREEN,true);
        pre.setLetterSpacing(0.09f); gap(root,10);
        TextView title = label("SCP ARCHIVE",35,TEXT,true); title.setLetterSpacing(0.025f);
        gap(root,5); label("Secure. Contain. Protect. Your portable archive.",13,MUTED,false); gap(root,23);
    }

    private void showHome() {
        openSlug = null;
        makePage(); header("Records");
        LinearLayout stat=panel(root);
        countView=text(db.count() + " ENTRIES ON DEVICE",17,GREEN,true); stat.addView(countView);
        gap(stat,5);
        stat.addView(text(offline()?"OFFLINE MODE ACTIVE  •  NO NETWORK REQUESTS":"LOCAL CATALOG  •  READY OFFLINE",11,MUTED,false));
        gap(root,14);
        LinearLayout actions=horizontal(); root.addView(actions,lp(-1,47));
        Button sync=button("↻  Sync latest",true,this::syncLatest);
        actions.addView(sync,new LinearLayout.LayoutParams(0,-1,1)); gapH(actions,8);
        Button offlineBtn=button("↓  Offline settings",false,this::showOfflineSettings);
        actions.addView(offlineBtn,new LinearLayout.LayoutParams(0,-1,1));
        gap(root,22);
        TextView heading=label("BROWSE THE FILES",12,MUTED,true); heading.setLetterSpacing(0.1f);
        gap(root,9);
        EditText find = new EditText(this); find.setSingleLine(true);
        find.setTextColor(TEXT); find.setHintTextColor(MUTED);
        find.setTextSize(15); find.setHint("Search SCP number, name, description...");
        find.setPadding(dp(14),dp(11),dp(14),dp(11)); find.setBackground(bg(PANEL2,12,EDGE));
        root.addView(find,lp(-1,49)); find.setText(search);
        find.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                search=s.toString(); visible=40; renderResults();
            }
            public void afterTextChanged(Editable e) {}
        });
        gap(root,13);
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=horizontal(); hscroll.addView(tabs);
        tab(tabs,"All records","all");tab(tabs,"★ Favorites","favorites");
        tab(tabs,"↓ Saved","saved");tab(tabs,"● New","new");
        root.addView(hscroll,lp(-1,43));
        gap(root,12);
        resultList=vertical(); root.addView(resultList,lp(-1,-2)); renderResults();
        gap(root,20);
        label("Unofficial SCP reader  •  SCP text: CC BY-SA 3.0",11,MUTED,false);
        label("Authors, image licenses, and source links are available on the original SCP Wiki pages.",11,MUTED,false);
    }
    private void gapH(LinearLayout parent,int w) { View g=new View(this); parent.addView(g,lp(w,1)); }
    private void tab(LinearLayout tabs, String title, String value) {
        Button b=button(title,value.equals(filter),() -> { filter=value;visible=40;showHome(); });
        tabs.addView(b,lp(-2,40));gapH(tabs,7);
    }
    private void renderResults() {
        if (resultList == null) return;
        resultList.removeAllViews();
        List<SCPData.Entry> entries=db.search(search,filter,visible,0);
        if (entries.isEmpty()) {
            TextView msg=text("No matching records. Try another search or sync the latest archive.",14,MUTED,false);
            msg.setPadding(0,dp(20),0,dp(20)); resultList.addView(msg); return;
        }
        for (SCPData.Entry entry : entries) {
            LinearLayout card=horizontal();card.setPadding(dp(15),dp(15),dp(13),dp(15));
            card.setBackground(bg(PANEL,14,EDGE));
            LinearLayout txt=vertical();card.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
            TextView id=text(entry.id.toUpperCase(),12,GREEN,true);id.setLetterSpacing(0.08f);txt.addView(id);
            gap(txt,4);txt.addView(text(entry.name,17,TEXT,true));
            gap(txt,5);
            String meta=(entry.classification.isEmpty()?"UNCLASSIFIED":entry.classification) +
                    (entry.summary.isEmpty()?"  ·  SUMMARY NOT SAVED":"  ·  SYNOPSIS READY") +
                    (entry.favorite?"  ·  ★":"");
            txt.addView(text(meta,10,MUTED,false));
            TextView arrow=text("›",27,GREEN,false);arrow.setPadding(dp(9),0,dp(2),0);
            card.addView(arrow);
            card.setOnClickListener(v -> showEntry(entry.slug));
            resultList.addView(card,lp(-1,-2));gap(resultList,8);
        }
        if (entries.size()==visible) {
            Button more=button("Load more records",false,() -> {visible+=40;renderResults();});
            resultList.addView(more,lp(-1,46));
        }
    }
    private void showEntry(String slug) {
        SCPData.Entry e=db.get(slug); if (e==null) return;
        openSlug=slug;makePage();
        Button back=button("‹  Back to archive",false,this::showHome);root.addView(back,lp(-2,42));
        gap(root,20);
        TextView id=label(e.id,15,GREEN,true);id.setLetterSpacing(.12f);
        gap(root,5);label(e.name,28,TEXT,true);gap(root,8);
        label((e.classification.isEmpty()?"CLASSIFICATION UNKNOWN":e.classification) +
              "   •   " + (e.favorite?"★ STARRED":"CASE FILE"),12,MUTED,true);
        gap(root,16);
        ImageView art=new ImageView(this);art.setAdjustViewBounds(true);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(bg(PANEL2,15,EDGE));
        art.setContentDescription("Reference photo for "+e.id);
        root.addView(art,lp(-1,205));
        if (e.image.isEmpty()) {
            art.setImageResource(android.R.drawable.ic_menu_gallery);
            art.setColorFilter(MUTED);
        } else {
            imageLoader.load(e.image,art,!offline());
        }
        gap(root,10);
        TextView caption=label(e.image.isEmpty()?"No verified page image in metadata.":
            "Page-sourced image. Check the article for individual media attribution and licensing.",11,MUTED,false);
        gap(root,17);
        LinearLayout controls=horizontal();root.addView(controls,lp(-1,44));
        Button star=button(e.favorite?"★  Favorited":"☆  Favorite",false,()->{
            db.setFavorite(slug,!db.get(slug).favorite);showEntry(slug);
        });
        controls.addView(star,new LinearLayout.LayoutParams(0,-1,1));gapH(controls,8);
        Button save=button(e.saved?"✓  Saved":"↓  Save entry",true,()->saveSingle(slug));
        controls.addView(save,new LinearLayout.LayoutParams(0,-1,1));
        gap(root,20);
        LinearLayout summary=panel(root);
        summary.addView(text("CASE SUMMARY",12,GREEN,true));gap(summary,10);
        if (e.summary.isEmpty()) {
            summary.addView(text("A description excerpt has not been downloaded for this entry.",15,MUTED,false));
            gap(summary,10);
            if (!offline() && !e.file.isEmpty()) {
                Button fetch=button("Get description excerpt",false,() -> fetchSummary(slug));
                summary.addView(fetch,lp(-1,44));
            }
        } else {
            summary.addView(text(e.summary,15,TEXT,false));gap(summary,10);
            summary.addView(text("Extracted from the source article's Description section. Not an official synopsis.",11,MUTED,false));
        }
        gap(root,15);
        LinearLayout source=panel(root);
        source.addView(text("SOURCE & ATTRIBUTION",12,GREEN,true));gap(source,8);
        source.addView(text("SCP Foundation Wiki" + (e.author.isEmpty()?"":"  •  Created by "+e.author),13,TEXT,false));
        source.addView(text(e.url,12,MUTED,false));gap(source,10);
        source.addView(text("SCP-derived content is shared under CC BY-SA 3.0. Individual images may require separate creator credits; see the source article's license box.",12,MUTED,false));
        gap(source,10);
        Button open=button("View original article ↗",false,()->{
            if (offline()) {toast("Turn off Offline Mode to visit the original article.");return;}
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(e.url)));
        });
        source.addView(open,lp(-1,45));gap(root,20);
        label("Some SCPs use unusual article formats. Extracts and images may be unavailable for certain entries.",12,MUTED,false);
    }

    private void showOfflineSettings() {
        openSlug = "settings";makePage();
        root.addView(button("‹  Back to archive",false,this::showHome),lp(-2,43));
        gap(root,20);label("OFFLINE LIBRARY",30,TEXT,true);gap(root,5);
        label("Your records remain readable without an internet connection.",14,MUTED,false);gap(root,18);
        LinearLayout p=panel(root);
        Switch mode = new Switch(this);mode.setText("Use Offline Mode");mode.setTextColor(TEXT);
        mode.setTextSize(16);mode.setChecked(offline());
        mode.setOnCheckedChangeListener((view,on)->{
            prefs.edit().putBoolean("offline",on).apply();
            if (on && busy) cancel.set(true);
            toast(on?"Offline Mode on. No network requests.":"Offline Mode off. Sync is available.");
        });p.addView(mode);gap(p,9);
        p.addView(text("Turning this on prevents new downloads and external article links. The local database and downloaded pictures still work.",12,MUTED,false));
        gap(root,17);
        LinearLayout sync=panel(root);
        sync.addView(text("BUILD YOUR OFFLINE COLLECTION",15,GREEN,true));gap(sync,10);
        sync.addView(text("1. Sync latest to get all listed SCP records. 2. Save the description excerpts. 3. Optionally cache all available pictures.",13,TEXT,false));
        gap(sync,12);
        sync.addView(button("↻  Update all SCP names / entries",true,this::syncLatest),lp(-1,47));gap(sync,8);
        sync.addView(button("↓  Download missing summaries",false,()->downloadSummaries(false)),lp(-1,47));gap(sync,8);
        sync.addView(button("↻  Refresh all summaries",false,()->downloadSummaries(true)),lp(-1,47));gap(sync,8);
        sync.addView(button("▧  Download all available pictures",false,this::downloadPictures),lp(-1,47));
        gap(root,16);
        LinearLayout state=panel(root);
        state.addView(text("DOWNLOAD STATUS",12,GREEN,true));gap(state,10);
        progressView=text("No active download.",13,TEXT,false);state.addView(progressView);
        gap(state,10);
        state.addView(button("Cancel current download",false,()->{
            cancel.set(true); if(progressView!=null)progressView.setText("Cancellation requested. Finishing the current file…");
        }),lp(-1,43));
        gap(root,14);
        label("Images can consume gigabytes. Some SCP pages have no image, or use unsupported formats. Keep the app open during large downloads.",12,MUTED,false);
        gap(root,7);
        label("Last index update: " + prefs.getString("last_sync","Not synced on this device"),12,MUTED,false);
    }

    private void state(String message) {
        ui(()->{if(progressView != null && "settings".equals(openSlug)) progressView.setText(message);});
    }
    private boolean canNetwork() {
        if (offline()) {toast("Switch off Offline Mode to download updates.");return false;}
        if (busy) {toast("A download is already running.");return false;}
        return true;
    }
    private void syncLatest() {
        if (!canNetwork()) return;
        new AlertDialog.Builder(this).setTitle("Sync the SCP archive?")
            .setMessage("Download the current SCP metadata index. Existing offline descriptions, favorites, and pictures are retained.")
            .setPositiveButton("Sync",(d,w)->{
                busy=true;cancel.set(false);state("Fetching latest index…");
                io.submit(()->{
                    try {
                        JSONObject index=ArchiveNet.json(SCPData.INDEX,30*1024*1024);
                        if (cancel.get()) throw new InterruptedException("Canceled");
                        if (index.length()<1000) throw new IllegalStateException("The catalog response is incomplete");
                        int added=db.merge(index,true,true);
                        // Canonical item names live on the wiki's series pages, not always in article titles.
                        for (int series=1;series<=10 && !cancel.get();series++) {
                            try {
                                String source="https://scp-wiki.wikidot.com/scp-series" + (series==1?"":"-"+series);
                                String html=new String(ArchiveNet.request(source,4*1024*1024),StandardCharsets.UTF_8);
                                Map<String,String> titles=SCPData.seriesNames(html);
                                if (!titles.isEmpty()) db.updateNames(titles);
                            } catch (Exception ignored) { /* Metadata update still succeeds. */ }
                        }
                        prefs.edit().putString("last_sync",java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())).apply();
                        ui(()->{
                            busy=false;state("Updated. New entries: " + added + ". Total on device: " + db.count());
                            toast("SCP archive updated. " + added + " new entries.");
                            if (!"settings".equals(openSlug)) showHome();
                        });
                    } catch (Exception ex) {failure("Index sync",ex);}
                });
            }).setNegativeButton("Cancel",null).show();
    }
    private void downloadSummaries(boolean refresh) {
        if (!canNetwork()) return;
        new AlertDialog.Builder(this).setTitle(refresh?"Refresh every synopsis?":"Download description excerpts?")
            .setMessage("Downloads series content files and extracts each article's Description section for offline use. This can consume considerable data, and some formats cannot be summarized automatically.")
            .setPositiveButton("Start",(d,w)->{
                busy=true;cancel.set(false);
                io.submit(()->{
                    int changes=0,errors=0;
                    List<String> files=db.contentFiles(!refresh);
                    for (int i=0;i<files.size();i++) {
                        if(cancel.get())break;
                        state("Processing series " + (i+1) + " of " + files.size() + " • " + changes + " descriptions stored");
                        try {
                            JSONObject content=ArchiveNet.json(SCPData.safeContentUrl(files.get(i)),150*1024*1024);
                            changes += db.mergeDescriptions(content,refresh);
                        } catch (Exception ex) {errors++;}
                    }
                    int total=changes,failed=errors;
                    ui(()->{busy=false;state((cancel.get()?"Stopped. ":"Finished. ") + total + " excerpts saved. " + failed + " content files skipped.");});
                });
            }).setNegativeButton("Cancel",null).show();
    }
    private void downloadPictures() {
        if (!canNetwork()) return;
        new AlertDialog.Builder(this).setTitle("Download the entire image library?")
            .setMessage("This may use gigabytes of mobile data and device storage. Only page-hosted supported pictures will be attempted. You can cancel between images.")
            .setPositiveButton("Download",(d,w)->{
                busy=true;cancel.set(false);
                io.submit(()->{
                    List<SCPData.Entry> all=db.allImages();int saved=0,failed=0;
                    for(int i=0;i<all.size();i++) {
                        if(cancel.get())break;
                        state("Image " + (i+1) + " of " + all.size() + " • downloaded " + saved + " • skipped " + failed);
                        try { ArchiveNet.cacheImage(getImageRoot(),all.get(i).image);saved++; }
                        catch(Exception ex) { failed++; }
                    }
                    int okay=saved,fail=failed;
                    ui(()->{busy=false;state((cancel.get()?"Stopped. ":"Finished. ") + okay + " images stored. " + fail + " skipped.");});
                });
            }).setNegativeButton("Cancel",null).show();
    }
    private void saveSingle(String slug) {
        SCPData.Entry e=db.get(slug); if(e==null)return;
        if(e.saved){db.setSaved(slug,false);showEntry(slug);return;}
        db.setSaved(slug,true);
        if(offline()) {showEntry(slug);return;}
        io.submit(()->{
            try {
                if(e.summary.isEmpty()&&!e.file.isEmpty()) {
                    JSONObject content=ArchiveNet.json(SCPData.safeContentUrl(e.file),150*1024*1024);
                    db.mergeDescriptions(content,false);
                }
                if(!e.image.isEmpty()) ArchiveNet.cacheImage(getImageRoot(),e.image);
                ui(()->{toast("Entry saved for offline viewing.");if(slug.equals(openSlug))showEntry(slug);});
            } catch(Exception ex) {
                ui(()->{toast("Saved the record. Some content or images could not be downloaded.");if(slug.equals(openSlug))showEntry(slug);});
            }
        });
        showEntry(slug);
    }
    private void fetchSummary(String slug) {
        if(!canNetwork())return;
        SCPData.Entry e=db.get(slug);
        if(e==null || e.file.isEmpty())return;
        busy=true;
        io.submit(()->{
            try {
                JSONObject content=ArchiveNet.json(SCPData.safeContentUrl(e.file),150*1024*1024);
                int n=db.mergeDescriptions(content,false);
                ui(()->{
                    busy=false;
                    if(slug.equals(openSlug))showEntry(slug);
                    if(n==0)toast("This article has no extractable Description section.");
                });
            } catch(Exception ex){failure("Description download",ex);}
        });
    }
    private void failure(String step,Exception ex) {
        ui(()->{busy=false;state(step + " failed: " + ex.getMessage());toast(step + " failed. Local files are unchanged.");});
    }
    private File getImageRoot() {return new File(getFilesDir(),"cached_pictures");}

    private class FileImageLoader {
        void load(String url,ImageView target,boolean mayDownload) {
            io.submit(()->{
                try {
                    File f=ArchiveNet.imageFile(getImageRoot(),url);
                    if(!f.exists() && mayDownload)f=ArchiveNet.cacheImage(getImageRoot(),url);
                    android.graphics.Bitmap bitmap=ArchiveNet.bitmap(f,900);
                    if(bitmap!=null)ui(()->{
                        if(target.getParent()!=null)target.setImageBitmap(bitmap);
                    });
                    else ui(()->target.setImageResource(android.R.drawable.ic_menu_gallery));
                } catch(Exception ex) {ui(()->target.setImageResource(android.R.drawable.ic_menu_gallery));}
            });
        }
    }
    @Override public void onBackPressed() {
        if(openSlug!=null)showHome();else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        cancel.set(true);io.shutdownNow();db.close();super.onDestroy();
    }
}
