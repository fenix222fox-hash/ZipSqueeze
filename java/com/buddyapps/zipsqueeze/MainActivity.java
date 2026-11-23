package com.buddyapps.zipsqueeze;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.database.Cursor;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_ZIP = 10, PICK_DIR = 11;
    private Uri archiveUri, destinationUri;
    private TextView archiveText, destinationText, statusText, statsText;
    private ProgressBar progress;
    private CheckBox deleteSource;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private long bytesDone;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildUi();
        Uri incoming = getIntent().getData();
        if (incoming != null) { archiveUri = incoming; updateSelections(); }
        if (Intent.ACTION_SEND.equals(getIntent().getAction()) && getIntent().getParcelableExtra(Intent.EXTRA_STREAM) != null) {
            archiveUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM); updateSelections();
        }
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24)); root.setBackgroundColor(Color.rgb(12, 18, 28));
        scroll.addView(root); setContentView(scroll);
        TextView brand = new TextView(this); brand.setText("ZipSqueeze"); brand.setTextSize(30); brand.setTypeface(null, Typeface.BOLD); brand.setTextColor(Color.WHITE); root.addView(brand);
        TextView subtitle = new TextView(this); subtitle.setText("Extração ZIP segura, em streaming"); subtitle.setTextSize(15); subtitle.setTextColor(Color.rgb(165,190,210)); addGap(root, 4); root.addView(subtitle);
        addGap(root, 24);
        archiveText = label("Arquivo ZIP não selecionado"); root.addView(archiveText);
        Button chooseZip = button("Escolher arquivo ZIP"); root.addView(chooseZip); chooseZip.setOnClickListener(v -> pickArchive());
        addGap(root, 12);
        destinationText = label("Destino não selecionado"); root.addView(destinationText);
        Button chooseDir = button("Escolher pasta de destino"); root.addView(chooseDir); chooseDir.setOnClickListener(v -> pickDestination());
        deleteSource = new CheckBox(this); deleteSource.setText("Excluir o ZIP ao terminar com sucesso"); deleteSource.setTextColor(Color.WHITE); deleteSource.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(105,210,180))); root.addView(deleteSource);
        TextView note = label("O ZIP só é removido depois que todas as entradas são extraídas e validadas. ZIP padrão não permite recuperar espaço de forma segura entrada a entrada sem reescrever o arquivo."); note.setTextSize(13); note.setTextColor(Color.rgb(180,190,205)); root.addView(note);
        addGap(root, 22);
        Button preview = button("Pré-visualizar conteúdo"); root.addView(preview); preview.setOnClickListener(v -> preview());
        Button start = button("Iniciar extração"); start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(82,205,170))); root.addView(start); start.setOnClickListener(v -> startExtraction());
        Button cancel = button("Cancelar extração"); root.addView(cancel); cancel.setOnClickListener(v -> { cancelled = true; status("Cancelamento solicitado. Arquivo de origem preservado."); });
        addGap(root, 20);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        statusText = label("Escolha um ZIP e uma pasta de destino."); addGap(root, 8); root.addView(statusText);
        statsText = label(""); root.addView(statsText);
    }
    private TextView label(String s) { TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(15); t.setPadding(0, dp(7), 0, dp(7)); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(40,60,78))); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8); b.setLayoutParams(p); return b; }
    private void addGap(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private void pickArchive() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i, PICK_ZIP); }
    private void pickDestination() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i, PICK_DIR); }
    @Override protected void onActivityResult(int req, int res, Intent data) { super.onActivityResult(req,res,data); if(res != RESULT_OK || data == null) return; Uri u=data.getData(); if(req==PICK_ZIP) { archiveUri=u; try { getContentResolver().takePersistableUriPermission(u, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {} } else if(req==PICK_DIR) { destinationUri=u; try { getContentResolver().takePersistableUriPermission(u, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch(Exception ignored) {} } updateSelections(); }
    private String displayName(Uri u) { if(u==null)return ""; Cursor c=null; try { c=getContentResolver().query(u,null,null,null,null); if(c!=null&&c.moveToFirst()) { int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(n>=0)return c.getString(n); } } catch(Exception ignored) {} finally { if(c!=null)c.close(); } return u.getLastPathSegment(); }
    private void updateSelections() { if(archiveText!=null) archiveText.setText(archiveUri==null?"Arquivo ZIP não selecionado":"ZIP: "+displayName(archiveUri)); if(destinationText!=null) destinationText.setText(destinationUri==null?"Destino não selecionado":"Destino: pasta escolhida"); }
    private void status(String s) { runOnUiThread(() -> statusText.setText(s)); }
    private void preview() { if(archiveUri==null){status("Escolha um arquivo ZIP primeiro.");return;} status("Lendo o índice ZIP…"); worker.execute(() -> { List<String> names=new ArrayList<>(); try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(getContentResolver().openInputStream(archiveUri)))) { ZipEntry e; while((e=z.getNextEntry())!=null && names.size()<250) { names.add((e.isDirectory()?"📁 ":"📄 ")+e.getName()); z.closeEntry(); } } catch(Exception e) { status("Não foi possível ler o ZIP: "+e.getMessage()); return; } StringBuilder b=new StringBuilder("Conteúdo (máx. 250 entradas):\n"); for(String n:names)b.append(n).append('\n'); final String text=b.toString(); runOnUiThread(() -> new android.app.AlertDialog.Builder(this).setTitle("Pré-visualização").setMessage(text).setPositiveButton("Fechar",null).show()); }); }
    private void startExtraction() { if(archiveUri==null||destinationUri==null){status("Selecione o ZIP e a pasta de destino.");return;} cancelled=false; bytesDone=0; progress.setProgress(0); status("Extraindo… o ZIP original será mantido em caso de falha."); worker.execute(this::extractZip); }
    private void extractZip() { long total=0; int files=0; try(InputStream raw=getContentResolver().openInputStream(archiveUri); ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))) { byte[] buf=new byte[64*1024]; ZipEntry e; while((e=zip.getNextEntry())!=null) { if(cancelled) throw new IOException("Cancelado pelo usuário"); String name=e.getName(); if(!safeName(name)) throw new IOException("Caminho inseguro no ZIP: "+name); if(e.isDirectory()){ ensureDirectory(name); zip.closeEntry(); continue; }
                Uri out=createOutput(name); long entryBytes=0; try(OutputStream os=getContentResolver().openOutputStream(out,"w")) { if(os==null) throw new IOException("O provedor não abriu o arquivo de saída"); int n; while((n=zip.read(buf))!=-1){ if(cancelled) throw new IOException("Cancelado pelo usuário"); os.write(buf,0,n); entryBytes+=n; total+=n; } }
                // ZipInputStream checks CRC when entry reaches EOF. closeEntry completes that check.
                zip.closeEntry(); files++; final int count=files; final long done=total; runOnUiThread(() -> { statsText.setText(count+" arquivo(s) · "+format(done)+" extraídos e verificados por CRC"); progress.setIndeterminate(true); }); status("Extraídos "+files+" arquivo(s)…"); }
            if(deleteSource.isChecked()) { try { DocumentsContract.deleteDocument(getContentResolver(),archiveUri); } catch(Exception ex) { status("Extração concluída; o provedor não permitiu excluir o ZIP original."); return; } }
            progress.setIndeterminate(false); progress.setProgress(100); status("Concluído: "+files+" arquivo(s), "+format(total)+". CRC validado.");
        } catch(Exception e) { progress.setIndeterminate(false); status("Extração interrompida: "+e.getMessage()+". O ZIP original foi preservado."); } }
    private boolean safeName(String n) { if(n==null||n.length()==0||n.startsWith("/")||n.startsWith("\\"))return false; String x=n.replace('\\','/'); for(String p:x.split("/")) if("..".equals(p)) return false; return true; }
    private Uri createOutput(String path) throws Exception { return resolvePath(path, false); }
    private void ensureDirectory(String path) throws Exception { String s=path.endsWith("/")?path.substring(0,path.length()-1):path; if(s.length()>0)resolvePath(s, true); }
    private Uri resolvePath(String path, boolean finalDirectory) throws Exception { String[] parts=path.replace('\\','/').split("/"); Uri parent=destinationUri; for(int i=0;i<parts.length;i++){ if(parts[i].length()==0)continue; boolean dir=i<parts.length-1 || finalDirectory; String mime=dir?DocumentsContract.Document.MIME_TYPE_DIR:guessMime(parts[i]); Uri child=findChild(parent,parts[i]); if(child==null) child=DocumentsContract.createDocument(getContentResolver(),parent,mime,parts[i]); if(child==null)throw new IOException("Falha criando "+parts[i]); parent=child; } return parent; }
    private Uri findChild(Uri parent,String name) { Cursor c=null; try { Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(parent,DocumentsContract.getDocumentId(parent)); c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null); while(c!=null&&c.moveToNext()) if(name.equals(c.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(parent,c.getString(0)); } catch(Exception ignored) {} finally { if(c!=null)c.close(); } return null; }
    private String guessMime(String n) { String x=n.toLowerCase(); if(x.endsWith(".txt")||x.endsWith(".log")||x.endsWith(".csv"))return "text/plain"; if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return "image/jpeg"; if(x.endsWith(".png"))return "image/png"; if(x.endsWith(".pdf"))return "application/pdf"; return "application/octet-stream"; }
    private String format(long n) { if(n<1024)return n+" B"; if(n<1024*1024)return String.format("%.1f KiB",n/1024.0); if(n<1024L*1024*1024)return String.format("%.1f MiB",n/(1024.0*1024)); return String.format("%.2f GiB",n/(1024.0*1024*1024)); }
}
