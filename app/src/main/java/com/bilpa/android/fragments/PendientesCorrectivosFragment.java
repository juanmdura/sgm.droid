package com.bilpa.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bilpa.android.BilpaApp;
import com.bilpa.android.CorrectivoActivoActivity;
import com.bilpa.android.CorrectivoPendienteActivity;
import com.bilpa.android.R;
import com.bilpa.android.SolucionDetailActivity;
import com.bilpa.android.adapters.CPendientesAdapter;
import com.bilpa.android.model.Pendiente;
import com.bilpa.android.model.correctivos.CActivo;
import com.bilpa.android.model.correctivos.Correctivo;
import com.bilpa.android.services.ApiCorrectivos;
import com.bilpa.android.services.ApiService;
import com.bilpa.android.services.AsyncCallback;
import com.bilpa.android.services.actions.BaseResult;
import com.bilpa.android.services.actions.pendientes.PendientesResult;
import com.bilpa.android.utils.Consts;
import com.bilpa.android.utils.SessionStore;
import com.mautibla.utils.ToastUtils;
import com.mautibla.utils.ViewUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class PendientesCorrectivosFragment extends Fragment implements
        AdapterView.OnItemClickListener, View.OnClickListener {

    public static final String TAG_DISCARD_DIALOG = "DISCARD_DIALOG";


    private SimpleDateFormat sdfPlazo = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private Correctivo mCorrectivo;
    public CActivo mActivo;
    public PendientesResult mPendientesResult;



    private ListView vList;
    private ProgressBar vProgress;
    private TextView vEmptyView;
    private CPendientesAdapter mPendientesAdapter;


    public static PendientesCorrectivosFragment newInstance(Correctivo correctivo, CActivo activo) {
        PendientesCorrectivosFragment fragment = new PendientesCorrectivosFragment();
        fragment.mCorrectivo = correctivo;
        fragment.mActivo = activo;
        return fragment;
    }

    public PendientesCorrectivosFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_pendientes_correctivo, container, false);

        vList = (ListView) v.findViewById(R.id.vList);
        vProgress = (ProgressBar) v.findViewById(R.id.vProgress);
        vEmptyView = (TextView) v.findViewById(R.id.vEmptyView);

        vList.setEmptyView(vEmptyView);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        if (savedInstanceState != null && savedInstanceState.containsKey("mPendientesResult")) {
            mPendientesResult = (PendientesResult) savedInstanceState.get("mPendientesResult");
        }

        ViewUtils.gone(vEmptyView, vProgress, vList);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        load();
    }

    private void load() {

        ViewUtils.gone(vEmptyView, vProgress, vList);

        if (mPendientesResult == null) {
            ViewUtils.visible(vProgress);

            ApiCorrectivos.getPendientes(mActivo.id, new AsyncCallback<PendientesResult>(getActivity()) {
                @Override
                protected void onSuccess(PendientesResult result) {
                    mPendientesResult = result;
                    bind(result);
                }
                @Override
                protected void onServiceOperationFailOK() {
                    super.onServiceOperationFailOK();
                    getActivity().onBackPressed();
                }
            });

        } else {
            bind(mPendientesResult);
        }

    }

    private void bind(PendientesResult result) {
        ViewUtils.visible(vList, vEmptyView);
        ViewUtils.gone(vProgress);
        result.sortByPlazo();
        mPendientesAdapter = new CPendientesAdapter(getActivity(), result.pendientes);
        mPendientesAdapter.setOnMoreListener(this);
        mPendientesAdapter.setOnRepairListener(this);
        vList.setAdapter(mPendientesAdapter);
        vList.setOnItemClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("mPendientesResult", mPendientesResult);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        BilpaApp app = BilpaApp.getInstance();
        app.cancelPendingRequests(ApiService.GET_PENDIENTES);
        app.cancelPendingRequests(ApiService.DELETE_PENDIENTE);
        app.cancelPendingRequests(ApiService.DESCARTAR_PENDIENTE);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CPendientesAdapter adapter = (CPendientesAdapter) parent.getAdapter();
        Pendiente pendiente = adapter.getItem(position);

        if (Pendiente.State.REPARADO == pendiente.getStatus()) {

            new MaterialDialog.Builder(getActivity())
                    .title(R.string.correctivo_pendientes_pendiente_reparado_title)
                    .iconRes(R.drawable.ic_launcher)
                    .content(
                        String.format(getString(R.string.correctivo_pendientes_pendiente_reparado_msg),
                            pendiente.comentario,
                            pendiente.fechaReparado != null ? sdfPlazo.format(pendiente.fechaReparado) : getContext().getString(R.string.pendientes_reparado_empty),
                            pendiente.destinatario)
                    )
                    .positiveText(R.string.btn_aceptar)
                    .show();

            return;
        }

        Intent intent = new Intent(getActivity(), CorrectivoPendienteActivity.class);
        intent.putExtra("correctivo", mCorrectivo);
        intent.putExtra("activo", mActivo);
        intent.putExtra("pendiente", pendiente);
        startActivityForResult(intent, Consts.Request.CORRECTIVOS_PENDIENTE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Se creo o se edito un pendiente. Se recara el listado de pendientes.
        if (requestCode == Consts.Request.CORRECTIVOS_PENDIENTE && resultCode == Activity.RESULT_OK) {
            ((CorrectivoActivoActivity) getActivity()).mUpdateCorredigoOnBack = true;
            updatePendientes();
            return;
        }

        if (requestCode == Consts.Request.CORRECTIVOS_SOLUCION && resultCode == Activity.RESULT_OK) {
            updateSoluciones();
            updatePendientes();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(final View v) {

        final Pendiente pendiente = (Pendiente) v.getTag();

        switch (v.getId()) {

            case R.id.vMore:
                PopupMenu popupMenu = new PopupMenu(getContext(), v);
                popupMenu.inflate(R.menu.menu_pendientes_row_popup);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {

                            case R.id.vDelete:
                                new MaterialDialog.Builder(getActivity())
                                    .title(com.bilpa.android.R.string.app_name)
                                    .iconRes(R.drawable.ic_launcher)
                                    .content(String.format(getString(R.string.pendientes_list_delete_alert), pendiente.comentario))
                                    .positiveText(R.string.btn_aceptar)
                                    .negativeText(R.string.btn_cancelar)
                                    .callback(new MaterialDialog.ButtonCallback() {
                                        @Override
                                        public void onPositive(MaterialDialog dialog) {
                                            doDeletePendiente(pendiente);
                                        }
                                    })
                                    .show();
                                return true;

                            case R.id.vDiscard:
                                CommentDialogFragment commentFragment = CommentDialogFragment.newInstance();
                                commentFragment.setTitle(getString(R.string.pendientes_discard_comment_dialog_title));
                                commentFragment.setCommentHint(getString(R.string.pendientes_discard_comment_dialog_hint));
                                commentFragment.setOnCommentListener(new CommentDialogFragment.OnCommentListener() {
                                    @Override
                                    public void onComment(final String newComment) {
                                        if (TextUtils.isEmpty(newComment)) {
                                            ToastUtils.showToast(getContext(), R.string.pendientes_discard_comment_dialog_msg_empty_comment);
                                            return;
                                        }
                                        doDescartarPendiente(pendiente, newComment);
                                    }
                                });
                                commentFragment.show(getActivity().getSupportFragmentManager(), TAG_DISCARD_DIALOG);
                                return true;
                        }
                        return false;
                    }
                });
                MenuPopupHelper menuPopupHelper = new MenuPopupHelper(getContext(), (MenuBuilder) popupMenu.getMenu(), v);
                menuPopupHelper.setForceShowIcon(true);
                menuPopupHelper.show();
                break;

            case R.id.vRepair:
                Intent intent = new Intent(getActivity(), SolucionDetailActivity.class);
                intent.putExtra("correctivo", mCorrectivo);
                intent.putExtra("activo", mActivo);
                intent.putExtra("pendiente", pendiente);
                startActivityForResult(intent, Consts.Request.CORRECTIVOS_SOLUCION);
                break;
        }
    }

    private void doDescartarPendiente(final Pendiente pendiente, String motivoDescarte) {
        Long idPendiente = pendiente.id;
        Long idDescartador = Long.valueOf(SessionStore.getSession(getActivity()).id);
        ApiService.descartarPendiente(idPendiente, idDescartador, motivoDescarte, new AsyncCallback<BaseResult>(getContext()) {
            @Override
            protected void onSuccess(BaseResult result) {
                mPendientesResult.pendientes.remove(pendiente);
                mPendientesResult.sortByPlazo();
                mPendientesAdapter.notifyDataSetChanged();
            }
        });
    }

    private void doDeletePendiente(final Pendiente pendiente) {
        ApiCorrectivos.deletePendiente(pendiente.id, new AsyncCallback<BaseResult>(getActivity()) {
            @Override
            protected void onSuccess(BaseResult result) {
                mPendientesResult.pendientes.remove(pendiente);
                mPendientesResult.sortByPlazo();
                mPendientesAdapter.notifyDataSetChanged();
                updatePendientes();
            }
        });
    }

    public void invalidatePendientes() {
        mPendientesResult = null;
    }

    public void updatePendientes() {
        ((CorrectivoActivoActivity) getActivity()).mUpdateCorredigoOnBack = true;
        invalidatePendientes();
        load();
    }

    private void updateSoluciones() {
        ((CorrectivoActivoActivity) getActivity()).mUpdateCorredigoOnBack = true;
        ((CorrectivoActivoActivity) getActivity()).updateSoluciones();
    }

}
