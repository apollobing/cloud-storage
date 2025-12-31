window.APP_CONFIG = {

    // Link to your GitHub repository with the project
    githubLink: "https://github.com/apollobing/cloud-storage",

    // Name displayed in the header
    mainName: "CLOUD STORAGE",

    // Backend address. If empty - same URL with the same port.
    // If running backend and frontend via docker compose - put backend name in docker network here
    baseUrl: "",

    // API prefix of your backend
    baseApi: "/api",


    /*
    *
    * Form validation configuration
    *
    * */

    // If true - form will be validated,
    // errors will be displayed on input. Button will be active only with valid data
    // If false - form can be submitted without validation.
    validateLoginForm: true,
    validateRegistrationForm: true,

    // Valid username
    validUsername: {
        minLength: 5,
        maxLength: 20,
        pattern: "^[a-zA-Z0-9]+[a-zA-Z_0-9]*[a-zA-Z0-9]+$",
    },

    // Valid password
    validPassword: {
        minLength: 5,
        maxLength: 20,
        pattern: "^[a-zA-Z0-9!@#$%^&*(),.?\":{}|<>[\\]/`~+=-_';]*$",
    },

    // Valid folder name
    validFolderName: {
        minLength: 1,
        maxLength: 200,
        pattern: "^[^/\\\\:*?\"<>|]+$",
    },


    /*
    *
    * Utility configurations
    *
    * */

    // Allow moving selected files and folders by dragging to adjacent folders (drag n drop)
    isMoveAllowed: true,

    // Allow cut and paste files/folders. This uses /move endpoint - if you have it implemented, everything should work
    isCutPasteAllowed: true,

    // Allow custom context menu for file management (called with right mouse button - on one file or on selected ones)
    isFileContextMenuAllowed: true,

    // Allow shortcuts on the page - Ctrl+X, Ctrl+V, Del - on selected elements
    isShortcutsAllowed: true,

    // Set of utility functions for interacting with the frontend.
    functions: {

        // Function for mapping backend data format to frontend format.
        // If backend uses Sergey's format - no need to change.
        // What are the features of the FRONTEND format (if backend differs and you will implement your own functionality)
        // 1) path in frontend data must contain the full path to the object from the root folder.
        //    If object is a folder, path at the end must contain a slash
        // 2) The same applies to name - if object is a folder - there must be a slash at the end
        //    if your backend returns obj.name for folders without a slash at the end - in this
        //    function add a slash for folders at the end

        // This mapping assumes that obj.path from backend will come with a slash at the end.
        // If object is in root directory - obj.path is an empty string. and after formatting - path will be just the object name
        mapObjectToFrontFormat: (obj) => {
            return {
                lastModified: null,
                name: obj.name,
                size: obj.size,
                path: obj.path + obj.name, // Full format path is necessary for correct navigation
                folder: obj.type === "DIRECTORY" // Frontend uses simple boolean. If folder has a different name - change it
            }
        },

    }

};
