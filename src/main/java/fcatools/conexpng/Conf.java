package fcatools.conexpng;

import de.tudresden.inf.tcs.fcaapi.Concept;
import de.tudresden.inf.tcs.fcaapi.FCAImplication;
import de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException;
import de.tudresden.inf.tcs.fcalib.FullObject;
import fcatools.conexpng.gui.MainToolbar;
import fcatools.conexpng.gui.lattice.LatticeGraph;
import fcatools.conexpng.model.AssociationRule;
import fcatools.conexpng.model.FormalContext;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * Contains context, lattice, implications, filePath, snapshots etc.
 * <p>
 * Why 'Conf'? "Dependency Injection", e.g. for testing purposes a component can
 * be passed a "MockConfiguration" very easily and it is better to have a
 * central place for the program's state as opposed to have it scattered
 * throughout different classes. If you want you can see this class as the
 * "Model" in an MVC context.
 *
 */
public class Conf extends UndoManager {
    private static final long serialVersionUID = 1L;
    public String filePath;
    public Vector<String> lastOpened = new Vector<>(5);
    public FormalContext context;
    public Set<AssociationRule> associations;
    public Set<FCAImplication<String>> implications;
    public boolean unsavedChanges = false;
    public LatticeGraph lattice;
    public Set<Concept<String, FullObject<String, String>>> concepts;
    public GUIConf guiConf;

    private PropertyChangeSupport propertyChangeSupport;

    public Conf() {
        propertyChangeSupport = new PropertyChangeSupport(this);
        guiConf = new GUIConf();
    }

    public int getNumberOfConcepts() {
        if (concepts.isEmpty()) {
            startCalculation(StatusMessage.CALCULATINGCONCEPTS);
            concepts = context.getConceptsWithoutConsideredElements();
            endCalculation(StatusMessage.CALCULATINGCONCEPTS);
        }
        return concepts.size();
    }

    public void init(int rows, int columns) {
        context = new FormalContext(rows, columns);
        associations = new TreeSet<AssociationRule>();
        implications = new TreeSet<FCAImplication<String>>();
        concepts = new HashSet<Concept<String, FullObject<String, String>>>();
        guiConf = new GUIConf();
        lattice = new LatticeGraph();
        filePath = filePath.substring(0, filePath.lastIndexOf(System.getProperty("file.separator")) + 1)
                + "untitled.cex";
        newContext(context);
    }

    public void setNewFile(String filepath) {
        if (filePath.equals(filepath))
            return;
        lastOpened.remove(filepath);
        lastOpened.remove(filePath);
        if (new File(filePath).exists()) {
            lastOpened.add(0, this.filePath);
            if (lastOpened.size() > 5)
                lastOpened.remove(5);
        }
        filePath = filepath;
    }

    // Redo-Undo-Behavior
    // ///////////////////////////////////////////////////////////
    private Conf lastConf;
    private boolean undoRedoInProgress;

    public void saveConf() {
        lastConf = copy(this);
    }

    public Conf copy(Conf conf) {
        Conf copy = new Conf();
        copy.context = new FormalContext();
        copy.context.addAttributes(conf.context.getAttributes());
        try {
            copy.context.addObjects(conf.context.getObjects());
        } catch (IllegalObjectException e) {
            // should never happens
        }
        return copy;
    }

    @SuppressWarnings("serial")
    public void makeRedoable() {
        if (!undoRedoInProgress) {
            UndoableEdit undoableEdit = new AbstractUndoableEdit() {
                final Conf curConf = copy(Conf.this);
                final Conf lastConf = copy(Conf.this.lastConf);

                // Method that is called when we must redo the undone action
                public void redo() throws javax.swing.undo.CannotRedoException {
                    super.redo();
                    context = curConf.context;
                    undoRedoInProgress = true;
                    newContext(context);
                    undoRedoInProgress = false;
                    MainToolbar.getRedoButton().setEnabled(canRedo());
                    MainToolbar.getUndoButton().setEnabled(canUndo());
                }

                public void undo() throws javax.swing.undo.CannotUndoException {
                    super.undo();
                    context = lastConf.context;

                    undoRedoInProgress = true;
                    newContext(context);
                    undoRedoInProgress = false;
                    MainToolbar.getRedoButton().setEnabled(canRedo());
                    MainToolbar.getUndoButton().setEnabled(canUndo());
                }
            };

            // Add this undoable edit to the undo manager
            addEdit(undoableEdit);
            MainToolbar.getRedoButton().setEnabled(canRedo());
            MainToolbar.getUndoButton().setEnabled(canUndo());
        }
        lastConf = copy(this);

    }

    // Communication
    // /////////////////////////////////////////////////////////////77

    public void contextChanged() {
        this.context.clearConsidered();
        firePropertyChange(ContextChangeEvents.CONTEXTCHANGED, null, context);
    }

    public void newContext(FormalContext context) {
        cancelCalculations();
        this.context = context;
        this.context.clearConsidered();
        firePropertyChange(ContextChangeEvents.NEWCONTEXT, null, context);
    }

    public void attributeNameChanged(String oldName, String newName) {
        firePropertyChange(ContextChangeEvents.ATTRIBUTENAMECHANGED, oldName, newName);
    }

    public void showLabelsChanged() {
        firePropertyChange(ContextChangeEvents.LABELSCHANGED, null, null);
    }

    public void temporaryContextChanged() {
        firePropertyChange(ContextChangeEvents.TEMPORARYCONTEXTCHANGED, null, null);
    }

    private int starts = 0;
    private int stops = 0;

    public void startCalculation(StatusMessage status) {
        starts++;
        fireStatusBarPropertyChange(status, START);
    }

    public void endCalculation(StatusMessage status) {
        stops++;
        fireStatusBarPropertyChange(status, STOP);
    }

    public void cancelCalculations() {
        firePropertyChange(ContextChangeEvents.CANCELCALCULATIONS, null, null);
    }

    public boolean canBeSaved() {
        return starts <= stops;
    }

    public void loadedFile() {
        cancelCalculations();
        propertyChangeSupport.firePropertyChange(new PropertyChangeEvent(this, "filepath", filePath, filePath));
        firePropertyChange(ContextChangeEvents.LOADEDFILE, null, lattice);
    }

    @SuppressWarnings("serial")
    public class ContextChangeEvent extends PropertyChangeEvent {

        private ContextChangeEvents cce;

        public ContextChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        public ContextChangeEvent(Object source, ContextChangeEvents cce, Object oldValue, Object newValue) {
            this(source, cce.toString(), oldValue, newValue);
            this.cce = cce;
        }

        public ContextChangeEvents getName() {
            return cce;
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private void firePropertyChange(ContextChangeEvents cce, Object oldValue, Object newValue) {
        if (propertyChangeSupport.getPropertyChangeListeners().length != 0) {
            if (cce != ContextChangeEvents.LOADEDFILE) {
                unsavedChanges = true;
                MainToolbar.getSaveButton().setEnabled(true);
            }
            propertyChangeSupport.firePropertyChange(new ContextChangeEvent(this, cce, oldValue, newValue));
        }
    }

    public static final int START = 1;

    public static final int STOP = 2;

    public enum StatusMessage {

        LOADINGFILE("Loading the context"), SAVINGFILE("Saving the context"), CALCULATINGASSOCIATIONS(
                "Calculating the associations"), CALCULATINGIMPLICATIONS("Calculating the implications"), CALCULATINGCONCEPTS(
                "Calculating the concepts"), CALCULATINGLATTICE("Calculating the lattice");

        private StatusMessage(String name) {
            this.name = name;
        }

        private final String name;

        public String toString() {
            return name;
        }
    }

    public class StatusBarMessage extends PropertyChangeEvent {

        private static final long serialVersionUID = 1L;

        public StatusBarMessage(Object source, String propertyName, Object oldValue, Object newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        public StatusBarMessage(Object source, StatusMessage status, Object oldValue, Object newValue) {
            this(source, status.toString(), oldValue, newValue);
        }
    }

    private void fireStatusBarPropertyChange(StatusMessage status, int newValue) {
        if (status != StatusMessage.LOADINGFILE && newValue != START) {
            unsavedChanges = true;
            MainToolbar.getSaveButton().setEnabled(true);
        }
        propertyChangeSupport.firePropertyChange(new StatusBarMessage(this, status, 0, newValue));
    }
}
